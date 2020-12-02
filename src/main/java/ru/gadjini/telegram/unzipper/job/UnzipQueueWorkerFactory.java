package ru.gadjini.telegram.unzipper.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorker;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorkerFactory;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipDevice;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipMessageBuilder;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

@Component
public class UnzipQueueWorkerFactory implements QueueWorkerFactory<UnzipQueueItem> {

    public static final String DEFAULT_PASSWORD = "no";

    private static final Logger LOGGER = LoggerFactory.getLogger(UnzipQueueWorkerFactory.class);

    private Set<UnzipDevice> unzipDevices;

    private MessageService messageService;

    private UserService userService;

    private CommandStateService commandStateService;

    private InlineKeyboardService inlineKeyboardService;

    private UnzipMessageBuilder messageBuilder;

    private ArchiveExtractor extractProcessorFactory;

    @Autowired
    public UnzipQueueWorkerFactory(Set<UnzipDevice> unzipDevices, @Qualifier("messageLimits") MessageService messageService,
                                   UserService userService, CommandStateService commandStateService,
                                   InlineKeyboardService inlineKeyboardService, UnzipMessageBuilder messageBuilder, ArchiveExtractor extractProcessorFactory) {
        this.unzipDevices = unzipDevices;
        this.messageService = messageService;
        this.userService = userService;
        this.commandStateService = commandStateService;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageBuilder = messageBuilder;
        this.extractProcessorFactory = extractProcessorFactory;
    }

    @Override
    public QueueWorker createWorker(UnzipQueueItem item) {
        if (item.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            return new UnzipQueueWorker(item);
        } else if (item.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            return new ExtractFileQueueWorker(item);
        } else {
            return new ExtractAllQueueWorker(item);
        }
    }

    private UnzipDevice getCandidate(Format format) {
        for (UnzipDevice unzipDevice : unzipDevices) {
            if (unzipDevice.accept(format)) {
                return unzipDevice;
            }
        }

        throw new IllegalArgumentException("Candidate not found for " + format + ". Wtf?");
    }

    public class UnzipQueueWorker implements QueueWorker {

        private final Logger LOGGER = LoggerFactory.getLogger(UnzipQueueWorker.class);

        private UnzipDevice unzipDevice;

        private final UnzipQueueItem item;

        private final SmartTempFile in;

        private UnzipQueueWorker(UnzipQueueItem item) {
            this.unzipDevice = getCandidate(item.getType());
            this.in = item.getDownloadedFile();
            this.item = item;
        }

        @Override
        public void execute() {
            String size = MemoryUtils.humanReadableByteCount(item.getSize());
            LOGGER.debug("Start({}, {}, {}, {})", item.getUserId(), size, item.getFile().getFormat(), item.getFile().getFileId());
            UnzipState unzipState = initAndGetState(in.getAbsolutePath());
            if (unzipState == null) {
                return;
            }
            Locale locale = userService.getLocaleOrDefault(item.getUserId());
            UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, 0, locale);

            messageService.editMessage(EditMessageText.builder().chatId(String.valueOf(item.getUserId()))
                    .messageId(item.getProgressMessageId())
                    .text(filesList.getMessage())
                    .replyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(),
                            0, filesList.getOffset(), item.getId(), locale)).build(), false);
            commandStateService.setState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, unzipState);

            LOGGER.debug("Finish({}, {}, {})", item.getUserId(), size, item.getFile().getFormat());
        }

        @Override
        public void cancel() {
            commandStateService.deleteState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME);
        }

        @Override
        public void unhandledException(Throwable e) {
            if (in != null) {
                in.smartDelete();
            }
        }

        private UnzipState initAndGetState(String zipFile) {
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState == null) {
                return null;
            }
            unzipState.setArchivePath(zipFile);
            unzipState.setUnzipJobId(item.getId());
            List<ZipFileHeader> zipFiles = unzipDevice.getZipFiles(zipFile, DEFAULT_PASSWORD);
            int i = 1;
            for (ZipFileHeader file : zipFiles) {
                unzipState.getFiles().put(i++, file);
            }

            return unzipState;
        }
    }

    public class ExtractAllQueueWorker implements QueueWorker {

        private UnzipQueueItem item;

        private ExtractAllQueueWorker(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public void execute() {
            String size = MemoryUtils.humanReadableByteCount(item.getExtractFileSize());
            LOGGER.debug("Start extract all({}, {})", item.getUserId(), size);
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);

            ListIterator<ArchiveExtractor.ArchiveFile> archiveFilesIterator = extractProcessorFactory.getIterator(0, unzipState.getFiles());

            boolean allUploaded = true;
            while (archiveFilesIterator.hasNext()) {
                ArchiveExtractor.ArchiveFile archiveFile = archiveFilesIterator.next();
                ArchiveExtractor.ExtractedFile extract = archiveFile.extract(item.getUserId(), unzipState);
                extractProcessorFactory.sendExtractedFile(item, extract);
                if (extract.isNewMedia()) {
                    allUploaded = false;
                    break;
                }
            }
            if (allUploaded) {
                extractProcessorFactory.finishExtracting(item.getUserId(), item.getProgressMessageId(), unzipState);
            }
            LOGGER.debug("Finish extract all({}, {})", item.getUserId(), size);
        }
    }

    public class ExtractFileQueueWorker implements QueueWorker {

        private final Logger LOGGER = LoggerFactory.getLogger(ExtractFileQueueWorker.class);

        private final UnzipQueueItem item;

        private ExtractFileQueueWorker(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public void execute() {
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            ZipFileHeader fileHeader = unzipState.getFiles().get(item.getExtractFileId());
            String size = MemoryUtils.humanReadableByteCount(fileHeader.getSize());
            LOGGER.debug("Start({}, {})", item.getUserId(), size);

            ListIterator<ArchiveExtractor.ArchiveFile> iterator = extractProcessorFactory.getIterator(item.getExtractFileId() - 1, unzipState.getFiles());

            ArchiveExtractor.ExtractedFile extract = iterator.next().extract(item.getUserId(), unzipState);
            extractProcessorFactory.sendExtractedFile(item, extract);

            LOGGER.debug("Finish({}, {})", item.getUserId(), size);
        }
    }
}
