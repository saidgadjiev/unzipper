package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendDocument;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.updatemessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Progress;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.ProgressManager;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MediaMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorker;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueWorkerFactory;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.progress.Lang;
import ru.gadjini.telegram.unzipper.service.unzip.*;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class UnzipQueueWorkerFactory implements QueueWorkerFactory<UnzipQueueItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnzipQueueWorkerFactory.class);

    private Set<UnzipDevice> unzipDevices;

    private LocalisationService localisationService;

    private MessageService messageService;

    private MediaMessageService mediaMessageService;

    private FileManager fileManager;

    private TempFileService fileService;

    private UserService userService;

    private CommandStateService commandStateService;

    private InlineKeyboardService inlineKeyboardService;

    private UnzipMessageBuilder messageBuilder;

    private ProgressManager progressManager;

    @Autowired
    public UnzipQueueWorkerFactory(Set<UnzipDevice> unzipDevices,
                                   LocalisationService localisationService, @Qualifier("messageLimits") MessageService messageService,
                                   @Qualifier("forceMedia") MediaMessageService mediaMessageService, FileManager fileManager,
                                   TempFileService fileService, UserService userService, CommandStateService commandStateService,
                                   InlineKeyboardService inlineKeyboardService, UnzipMessageBuilder messageBuilder,
                                   ProgressManager progressManager) {
        this.unzipDevices = unzipDevices;
        this.localisationService = localisationService;
        this.messageService = messageService;
        this.mediaMessageService = mediaMessageService;
        this.fileManager = fileManager;
        this.fileService = fileService;
        this.userService = userService;
        this.commandStateService = commandStateService;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageBuilder = messageBuilder;
        this.progressManager = progressManager;
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

    private Progress extractAllProgress(UnzipQueueItem queueItem, int count, int current, long fileSize) {
        Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
        Progress progress = new Progress();
        progress.setLocale(locale.getLanguage());
        progress.setChatId(queueItem.getUserId());
        progress.setProgressMessageId(queueItem.getProgressMessageId());
        progress.setProgressMessage(messageBuilder.buildExtractAllProgressMessage(count, current, ExtractFileStep.UPLOADING,
                fileSize, queueItem.getQueuePosition(), Lang.PYTHON, locale));

        if (current < count) {
            String completionMessage = messageBuilder.buildExtractAllProgressMessage(count, current + 1, ExtractFileStep.EXTRACTING, fileSize,
                    queueItem.getQueuePosition(), Lang.JAVA, locale);
            String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
            progress.setAfterProgressCompletionMessage(String.format(completionMessage, 50, "10 " + seconds));
            progress.setAfterProgressCompletionReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale));
        }
        progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale));

        return progress;
    }

    private Progress extractFileProgress(UnzipQueueItem queueItem) {
        if (progressManager.isShowingUploadingProgress(queueItem.getSize())) {
            Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
            Progress progress = new Progress();
            progress.setLocale(locale.getLanguage());
            progress.setChatId(queueItem.getUserId());
            progress.setProgressMessageId(queueItem.getProgressMessageId());
            progress.setProgressMessage(messageBuilder.buildExtractFileProgressMessage(queueItem, ExtractFileStep.UPLOADING, Lang.PYTHON, locale));
            progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale));

            return progress;
        } else {
            return null;
        }
    }

    private Progress unzipProgress(UnzipQueueItem item) {
        Locale locale = userService.getLocaleOrDefault(item.getUserId());
        Progress progress = new Progress();
        progress.setLocale(locale.getLanguage());
        progress.setChatId(item.getUserId());
        progress.setProgressMessageId(item.getProgressMessageId());
        progress.setProgressMessage(messageBuilder.buildUnzipProgressMessage(item, UnzipStep.DOWNLOADING, Lang.PYTHON, locale));

        String completionMessage = messageBuilder.buildUnzipProgressMessage(item, UnzipStep.UNZIPPING, Lang.JAVA, locale);
        String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
        progress.setAfterProgressCompletionMessage(String.format(completionMessage, 50, "10 " + seconds));
        progress.setAfterProgressCompletionReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(item.getId(), locale));
        progress.setProgressReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(item.getId(), locale));

        return progress;
    }

    private void finishExtracting(UnzipQueueItem queueItem, UnzipState unzipState) {
        Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());

        UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
        InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);

        messageService.editMessage(new EditMessageText(queueItem.getUserId(), queueItem.getProgressMessageId(), filesList.getMessage())
                .setReplyMarkup(filesListKeyboard)
                .setThrowEx(true));
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

        private static final String TAG = "unzip";

        private final Logger LOGGER = LoggerFactory.getLogger(UnzipQueueWorker.class);

        private UnzipDevice unzipDevice;

        private final UnzipQueueItem item;

        private volatile SmartTempFile in;

        private UnzipQueueWorker(UnzipQueueItem item) {
            this.unzipDevice = getCandidate(item.getType());
            this.item = item;
        }

        @Override
        public void execute() {
            String size = MemoryUtils.humanReadableByteCount(item.getSize());
            LOGGER.debug("Start({}, {}, {}, {})", item.getUserId(), size, item.getFile().getFormat(), item.getFile().getFileId());
            in = fileService.createTempFile(item.getUserId(), item.getFile().getFileId(), TAG, item.getFile().getFormat().getExt());
            fileManager.forceDownloadFileByFileId(item.getFile().getFileId(), item.getSize(), unzipProgress(item), in);
            UnzipState unzipState = initAndGetState(in.getAbsolutePath());
            if (unzipState == null) {
                return;
            }
            Locale locale = userService.getLocaleOrDefault(item.getUserId());
            UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, 0, locale);

            messageService.editMessage(new EditMessageText(item.getUserId(), item.getProgressMessageId(), filesList.getMessage())
                    .setThrowEx(true)
                    .setReplyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(),
                            0, filesList.getOffset(), item.getId(), locale)));
            commandStateService.setState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, unzipState);

            LOGGER.debug("Finish({}, {}, {})", item.getUserId(), size, item.getFile().getFormat());
        }

        @Override
        public void cancel() {
            if (!fileManager.cancelDownloading(item.getFile().getFileId()) && in != null) {
                in.smartDelete();
            }
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
            List<ZipFileHeader> zipFiles = unzipDevice.getZipFiles(zipFile);
            int i = 1;
            for (ZipFileHeader file : zipFiles) {
                unzipState.getFiles().put(i++, file);
            }

            return unzipState;
        }
    }

    public class ExtractAllQueueWorker implements QueueWorker {

        private static final String TAG = "extractall";

        private static final int SLEEP_TIME = 2500;

        private UnzipQueueItem item;

        private Queue<SmartTempFile> files = new LinkedBlockingQueue<>();

        private ExtractAllQueueWorker(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public void execute() throws Exception {
            String size = MemoryUtils.humanReadableByteCount(item.getExtractFileSize());
            LOGGER.debug("Start extract all({}, {})", item.getUserId(), size);
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            Locale locale = userService.getLocaleOrDefault(item.getUserId());
            UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());

            int i = 1;
            for (Iterator<Map.Entry<Integer, ZipFileHeader>> iterator = unzipState.getFiles().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Integer, ZipFileHeader> entry = iterator.next();
                if (unzipState.getFilesCache().containsKey(entry.getKey())) {
                    String fileName = FilenameUtils.getName(entry.getValue().getPath());
                    mediaMessageService.sendFile(item.getUserId(), unzipState.getFilesCache().get(entry.getKey()), fileName);
                    String message = messageBuilder.buildExtractAllProgressMessage(unzipState.getFiles().size(), i + 1,
                            ExtractFileStep.EXTRACTING, entry.getValue().getSize(), item.getQueuePosition(), Lang.JAVA, locale);
                    String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
                    messageService.editMessage(new EditMessageText(item.getUserId(), item.getProgressMessageId(), String.format(message, 50, "7 " + seconds))
                            .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(item.getId(), locale)));
                } else {
                    SmartTempFile file = fileService.createTempFile(item.getUserId(), TAG, FilenameUtils.getExtension(entry.getValue().getPath()));
                    files.add(file);
                    unzipDevice.unzip(entry.getValue().getPath(), unzipState.getArchivePath(), file.getAbsolutePath());

                    String fileName = FilenameUtils.getName(entry.getValue().getPath());
                    SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) item.getUserId(), fileName, file.getFile())
                            .setProgress(extractAllProgress(item, unzipState.getFiles().size(), i, file.length()))
                            .setCaption(fileName));
                    if (result != null) {
                        unzipState.getFilesCache().put(entry.getKey(), result.getFileId());
                        commandStateService.setState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, unzipState);
                    }
                }

                if (iterator.hasNext()) {
                    Thread.sleep(SLEEP_TIME);
                }

                ++i;
            }
            LOGGER.debug("Finish extract all({}, {})", item.getUserId(), size);
        }

        @Override
        public void finish() {
            files.forEach(SmartTempFile::smartDelete);
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            finishExtracting(item, unzipState);
        }

        @Override
        public void cancel() {
            files.forEach(smartTempFile -> {
                if (!fileManager.cancelUploading(smartTempFile.getAbsolutePath())) {
                    smartTempFile.smartDelete();
                }
            });
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
        public void execute() throws Exception {
            String size;

            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            ZipFileHeader fileHeader = unzipState.getFiles().get(item.getExtractFileId());
            size = MemoryUtils.humanReadableByteCount(fileHeader.getSize());
            LOGGER.debug("Start({}, {})", item.getUserId(), size);

            UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());
            out = fileService.createTempFile(item.getUserId(), TAG, FilenameUtils.getExtension(fileHeader.getPath()));
            unzipDevice.unzip(fileHeader.getPath(), unzipState.getArchivePath(), out.getAbsolutePath());

            String fileName = FilenameUtils.getName(fileHeader.getPath());
            SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) item.getUserId(), fileName, out.getFile())
                    .setProgress(extractFileProgress(item))
                    .setCaption(fileName));
            if (result != null) {
                unzipState.getFilesCache().put(item.getExtractFileId(), result.getFileId());
                commandStateService.setState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, unzipState);
            }
            LOGGER.debug("Finish({}, {})", item.getUserId(), size);
        }

        @Override
        public void finish() {
            if (out != null) {
                out.smartDelete();
            }
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            finishExtracting(item, unzipState);
        }

        @Override
        public void cancel() {
            if (out != null && !fileManager.cancelUploading(out.getAbsolutePath())) {
                out.smartDelete();
            }
        }
    }
}
