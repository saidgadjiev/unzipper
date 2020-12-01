package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.Progress;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileUploadService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorker;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorkerFactory;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.unzip.ExtractFileStep;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipDevice;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipMessageBuilder;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class UnzipQueueWorkerFactory implements QueueWorkerFactory<UnzipQueueItem> {

    public static final String DEFAULT_PASSWORD = "no";

    private static final Logger LOGGER = LoggerFactory.getLogger(UnzipQueueWorkerFactory.class);

    private Set<UnzipDevice> unzipDevices;

    private MessageService messageService;

    private FileUploadService fileUploadService;

    private TempFileService fileService;

    private UserService userService;

    private CommandStateService commandStateService;

    private InlineKeyboardService inlineKeyboardService;

    private UnzipMessageBuilder messageBuilder;

    private AsteriskArchiveExtractor extractProcessorFactory;

    @Autowired
    public UnzipQueueWorkerFactory(Set<UnzipDevice> unzipDevices, @Qualifier("messageLimits") MessageService messageService,
                                   TempFileService fileService, UserService userService, CommandStateService commandStateService,
                                   InlineKeyboardService inlineKeyboardService, UnzipMessageBuilder messageBuilder, AsteriskArchiveExtractor extractProcessorFactory) {
        this.unzipDevices = unzipDevices;
        this.messageService = messageService;
        this.fileService = fileService;
        this.userService = userService;
        this.commandStateService = commandStateService;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageBuilder = messageBuilder;
        this.extractProcessorFactory = extractProcessorFactory;
    }

    @Autowired
    public void setFileUploadService(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
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

    private Progress extractFileProgress(UnzipQueueItem queueItem) {
        Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
        Progress progress = new Progress();
        progress.setChatId(queueItem.getUserId());
        progress.setProgressMessageId(queueItem.getProgressMessageId());
        progress.setProgressMessage(messageBuilder.buildExtractFileProgressMessage(queueItem, ExtractFileStep.UPLOADING, locale));
        progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale));

        return progress;
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

            for (Map.Entry<Integer, ZipFileHeader> entry : unzipState.getFiles().entrySet()) {
                AsteriskArchiveExtractor.ExtractedFile extract = extractProcessorFactory.createArchiveFile(entry).extract(item.getUserId(), unzipState);
                extractProcessorFactory.sendExtractedFile(item, extract);
                if (extract.isStop()) {
                    break;
                }
            }
            LOGGER.debug("Finish extract all({}, {})", item.getUserId(), size);
        }
    }

    public class ExtractFileQueueWorker implements QueueWorker {

        private static final String TAG = "extractfile";

        private final Logger LOGGER = LoggerFactory.getLogger(ExtractFileQueueWorker.class);

        private final UnzipQueueItem item;

        private volatile SmartTempFile out;

        private ExtractFileQueueWorker(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public void execute() {
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            ZipFileHeader fileHeader = unzipState.getFiles().get(item.getExtractFileId());
            String size = MemoryUtils.humanReadableByteCount(fileHeader.getSize());
            LOGGER.debug("Start({}, {})", item.getUserId(), size);

            UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());
            out = fileService.createTempFile(item.getUserId(), TAG, FilenameUtils.getExtension(fileHeader.getPath()));
            unzipDevice.unzip(fileHeader.getPath(), unzipState.getArchivePath(), out.getAbsolutePath(), DEFAULT_PASSWORD);

            String fileName = FilenameUtils.getName(fileHeader.getPath());
            SendDocument sendDocument = SendDocument.builder().chatId(String.valueOf(item.getUserId()))
                    .document(new InputFile(out.getFile(), fileName))
                    .caption(fileName).build();
            fileUploadService.createUpload(item.getUserId(), SendDocument.PATH, sendDocument, extractFileProgress(item),
                    item.getId(), new Extra(item.getItemType(), item.getExtractFileId(), item.getProgressMessageId(), -1, item.getQueuePosition()));
            LOGGER.debug("Finish({}, {})", item.getUserId(), size);
        }

        @Override
        public void cancel() {
            if (out != null) {
                out.smartDelete();
            }
        }
    }
}
