package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.domain.QueueItem;
import ru.gadjini.telegram.smart.bot.commons.exception.ProcessException;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendDocument;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendMessage;
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
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueJobDelegate;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.progress.Lang;
import ru.gadjini.telegram.unzipper.service.unzip.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class UnzipperJobDelegate implements QueueJobDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnzipperJobDelegate.class);

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
    public UnzipperJobDelegate(Set<UnzipDevice> unzipDevices,
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
    public WorkerTaskDelegate mapWorker(QueueItem queueItem) {
        return toTask((UnzipQueueItem) queueItem);
    }

    @Override
    public void currentTasksRemoved(int userId) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);

        if (unzipState != null) {
            LOGGER.debug("Remove previous state({})", userId);
            if (StringUtils.isNotBlank(unzipState.getArchivePath())) {
                new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
            }
            commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);
        }
    }

    private WorkerTaskDelegate toTask(UnzipQueueItem item) {
        if (item.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            return new UnzipTask(item);
        } else if (item.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            return new ExtractFileTask(item);
        } else {
            return new ExtractAllTask(item);
        }
    }

    private Progress extractAllProgress(int count, int current, long chatId, int jobId, int processMessageId, long fileSize) {
        if (progressManager.isShowingUploadingProgress(fileSize)) {
            Locale locale = userService.getLocaleOrDefault((int) chatId);
            Progress progress = new Progress();
            progress.setLocale(locale.getLanguage());
            progress.setChatId(chatId);
            progress.setProgressMessageId(processMessageId);
            progress.setProgressMessage(messageBuilder.buildExtractAllProgressMessage(count, current, ExtractFileStep.UPLOADING, fileSize, Lang.PYTHON, locale));

            if (current < count) {
                String completionMessage = messageBuilder.buildExtractAllProgressMessage(count, current + 1, ExtractFileStep.EXTRACTING, fileSize, Lang.JAVA, locale);
                String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
                progress.setAfterProgressCompletionMessage(String.format(completionMessage, 50, "10 " + seconds));
                progress.setAfterProgressCompletionReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));
            }
            progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));

            return progress;
        } else {
            return null;
        }
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

        messageService.editMessage(new EditMessageText(queueItem.getUserId(), queueItem.getProgressMessageId(), messageBuilder.buildExtractFileProgressMessage(queueItem, ExtractFileStep.COMPLETED, Lang.JAVA, locale)));
        messageService.sendMessage(new SendMessage((long) queueItem.getUserId(), filesList.getMessage())
                .setReplyMarkup(filesListKeyboard));
    }

    private UnzipDevice getCandidate(Format format) {
        for (UnzipDevice unzipDevice : unzipDevices) {
            if (unzipDevice.accept(format)) {
                return unzipDevice;
            }
        }

        throw new IllegalArgumentException("Candidate not found for " + format + ". Wtf?");
    }

    @Override
    public void afterTaskCanceled(QueueItem queueItem) {
        if (((UnzipQueueItem) queueItem).getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            commandStateService.deleteState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME);

            UnzipState unzipState = commandStateService.getState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null && StringUtils.isNotBlank(unzipState.getArchivePath())) {
                new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
            }
        } else {
            UnzipState unzipState = commandStateService.getState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null) {
                Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
                UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
                InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);
                messageService.editMessage(new EditMessageText(queueItem.getUserId(), queueItem.getProgressMessageId(), filesList.getMessage())
                        .setReplyMarkup(filesListKeyboard));
            }
        }
    }

    public class UnzipTask implements WorkerTaskDelegate {

        private static final String TAG = "unzip";

        private final Logger LOGGER = LoggerFactory.getLogger(UnzipTask.class);

        private UnzipDevice unzipDevice;

        private final UnzipQueueItem item;

        private volatile SmartTempFile in;

        private UnzipTask(UnzipQueueItem item) {
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

            messageService.editMessage(new EditMessageText(item.getUserId(), item.getProgressMessageId(), messageBuilder.buildUnzipProgressMessage(item, UnzipStep.COMPLETED, Lang.JAVA, locale)));
            messageService.sendMessage(new SendMessage((long) item.getUserId(), filesList.getMessage())
                    .setReplyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), item.getId(), locale)));
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

        @Override
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_UNZIP_ERROR;
            }

            return null;
        }

        @Override
        public boolean shouldBeDeletedAfterCompleted() {
            return true;
        }

        @Override
        public String getWaitingMessage(QueueItem queueItem, Locale locale) {
            return localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
        }

        @Override
        public InlineKeyboardMarkup getWaitingKeyboard(QueueItem queueItem, Locale locale) {
            return inlineKeyboardService.getUnzipProcessingKeyboard(queueItem.getId(), locale);
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

    public class ExtractAllTask implements WorkerTaskDelegate {

        private static final String TAG = "extractall";

        private static final int SLEEP_TIME = 2500;

        private UnzipQueueItem item;

        private Queue<SmartTempFile> files = new LinkedBlockingQueue<>();

        private ExtractAllTask(UnzipQueueItem item) {
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
                            ExtractFileStep.EXTRACTING, entry.getValue().getSize(), Lang.JAVA, locale);
                    String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
                    messageService.editMessage(new EditMessageText(item.getUserId(), item.getProgressMessageId(), String.format(message, 50, "7 " + seconds))
                            .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(item.getId(), locale)));
                } else {
                    SmartTempFile file = fileService.createTempFile(item.getUserId(), TAG, FilenameUtils.getExtension(entry.getValue().getPath()));
                    files.add(file);
                    unzipDevice.unzip(entry.getValue().getPath(), unzipState.getArchivePath(), file.getAbsolutePath());

                    String fileName = FilenameUtils.getName(entry.getValue().getPath());
                    SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) item.getUserId(), fileName, file.getFile())
                            .setProgress(extractAllProgress(unzipState.getFiles().size(), i, item.getUserId(), item.getId(), item.getProgressMessageId(), file.length()))
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
        public boolean shouldBeDeletedAfterCompleted() {
            return true;
        }

        @Override
        public void finish() {
            files.forEach(SmartTempFile::smartDelete);
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            finishExtracting(item, unzipState);
        }

        @Override
        public String getWaitingMessage(QueueItem queueItem, Locale locale) {
            return localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
        }

        @Override
        public InlineKeyboardMarkup getWaitingKeyboard(QueueItem queueItem, Locale locale) {
            return inlineKeyboardService.getUnzipProcessingKeyboard(queueItem.getId(), locale);
        }

        @Override
        public void cancel() {
            files.forEach(smartTempFile -> {
                if (!fileManager.cancelUploading(smartTempFile.getAbsolutePath())) {
                    smartTempFile.smartDelete();
                }
            });
        }

        @Override
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_UNZIP_ERROR;
            }

            return null;
        }
    }

    public class ExtractFileTask implements WorkerTaskDelegate {

        private static final String TAG = "extractfile";

        private final Logger LOGGER = LoggerFactory.getLogger(ExtractFileTask.class);

        private final UnzipQueueItem item;

        private volatile SmartTempFile out;

        private ExtractFileTask(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public void execute() {
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
        public InlineKeyboardMarkup getWaitingKeyboard(QueueItem queueItem, Locale locale) {
            return inlineKeyboardService.getUnzipProcessingKeyboard(queueItem.getId(), locale);
        }

        @Override
        public void cancel() {
            if (out != null && !fileManager.cancelUploading(out.getAbsolutePath())) {
                out.smartDelete();
            }
        }

        @Override
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_EXTRACT_FILE_ERROR;
            }

            return null;
        }

        @Override
        public String getWaitingMessage(QueueItem queueItem, Locale locale) {
            return localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
        }

        @Override
        public boolean shouldBeDeletedAfterCompleted() {
            return true;
        }
    }
}
