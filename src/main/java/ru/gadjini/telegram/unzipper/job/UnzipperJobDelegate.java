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
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.AnswerCallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Progress;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.ProgressManager;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MediaMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueJobDelegate;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueService;
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

    private SmartExecutorService executor;

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

    private QueueService queueService;

    @Autowired
    public UnzipperJobDelegate(Set<UnzipDevice> unzipDevices,
                               LocalisationService localisationService, @Qualifier("messageLimits") MessageService messageService,
                               @Qualifier("forceMedia") MediaMessageService mediaMessageService, FileManager fileManager,
                               TempFileService fileService, UserService userService, CommandStateService commandStateService,
                               InlineKeyboardService inlineKeyboardService, UnzipMessageBuilder messageBuilder,
                               ProgressManager progressManager, QueueService queueService) {
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
        this.queueService = queueService;
    }

    @Autowired
    public void setExecutor(@Qualifier("queueTaskExecutor") SmartExecutorService executor) {
        this.executor = executor;
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
            progress.setProgressMessage(messageBuilder.buildExtractAllProgressMessage(count, current, ExtractFileStep.UPLOADING, Lang.PYTHON, locale));

            if (current < count) {
                String completionMessage = messageBuilder.buildExtractAllProgressMessage(count, current + 1, ExtractFileStep.EXTRACTING, Lang.JAVA, locale);
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

    private Progress extractFileProgress(long chatId, int jobId, int processMessageId, long fileSize) {
        if (progressManager.isShowingUploadingProgress(fileSize)) {
            Locale locale = userService.getLocaleOrDefault((int) chatId);
            Progress progress = new Progress();
            progress.setLocale(locale.getLanguage());
            progress.setChatId(chatId);
            progress.setProgressMessageId(processMessageId);
            progress.setProgressMessage(messageBuilder.buildExtractFileProgressMessage(ExtractFileStep.UPLOADING, Lang.PYTHON, locale));
            progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));

            return progress;
        } else {
            return null;
        }
    }

    private Progress unzipProgress(long chatId, int jobId, int processMessageId) {
        Locale locale = userService.getLocaleOrDefault((int) chatId);
        Progress progress = new Progress();
        progress.setLocale(locale.getLanguage());
        progress.setChatId(chatId);
        progress.setProgressMessageId(processMessageId);
        progress.setProgressMessage(messageBuilder.buildUnzipProgressMessage(UnzipStep.DOWNLOADING, Lang.PYTHON, locale));

        String completionMessage = messageBuilder.buildUnzipProgressMessage(UnzipStep.UNZIPPING, Lang.JAVA, locale);
        String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
        progress.setAfterProgressCompletionMessage(String.format(completionMessage, 50, "10 " + seconds));
        progress.setAfterProgressCompletionReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(jobId, locale));
        progress.setProgressReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(jobId, locale));

        return progress;
    }

    private void finishExtracting(int userId, int messageId, UnzipState unzipState) {
        Locale locale = userService.getLocaleOrDefault(userId);

        UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
        InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);

        messageService.editMessage(new EditMessageText(userId, messageId, messageBuilder.buildExtractFileProgressMessage(ExtractFileStep.COMPLETED, Lang.JAVA, locale)));
        messageService.sendMessage(new SendMessage((long) userId, filesList.getMessage())
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

    public void cancelUnzip(long chatId, int messageId, String queryId, int jobId) {
        UnzipState unzipState = commandStateService.getState(chatId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null || unzipState.getUnzipJobId() != jobId) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_ITEM_NOT_FOUND, userService.getLocaleOrDefault((int) chatId)),
                    true
            ));
        } else {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_CANCELED, userService.getLocaleOrDefault((int) chatId))
            ));
            if (!executor.cancelAndComplete(jobId, true)) {
                UnzipQueueItem unzipQueueItem = (UnzipQueueItem) queueService.deleteAndGetById(jobId);
                if (unzipQueueItem != null) {
                    fileManager.fileWorkObject(chatId, unzipQueueItem.getFile().getSize()).stop();
                }

                commandStateService.deleteState(chatId, UnzipCommandNames.START_COMMAND_NAME);
                if (StringUtils.isNotBlank(unzipState.getArchivePath())) {
                    new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
                }
            }
        }
        messageService.editMessage(new EditMessageText(
                chatId, messageId, localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_CANCELED, userService.getLocaleOrDefault((int) chatId))));
    }

    public void cancelExtractFile(long chatId, int messageId, String queryId, int jobId) {
        if (!queueService.exists(jobId)) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_ITEM_NOT_FOUND, userService.getLocaleOrDefault((int) chatId)),
                    true
            ));
            messageService.editMessage(new EditMessageText(
                    chatId, messageId, localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_CANCELED, userService.getLocaleOrDefault((int) chatId))));
        } else {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_QUERY_CANCELED, userService.getLocaleOrDefault((int) chatId))
            ));
            if (!executor.cancelAndComplete(jobId, true)) {
                queueService.deleteById(jobId);
            }
            UnzipState unzipState = commandStateService.getState(chatId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null) {
                Locale locale = userService.getLocaleOrDefault((int) chatId);
                UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
                InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);
                messageService.editMessage(new EditMessageText(chatId, messageId, filesList.getMessage())
                        .setReplyMarkup(filesListKeyboard));
            }
        }
    }

    public class UnzipTask implements WorkerTaskDelegate {

        private static final String TAG = "unzip";

        private final Logger LOGGER = LoggerFactory.getLogger(UnzipTask.class);

        private final int jobId;
        private final int userId;
        private final String fileId;
        private final long fileSize;
        private final Format format;
        private final int messageId;
        private UnzipDevice unzipDevice;
        private volatile SmartTempFile in;

        private UnzipTask(UnzipQueueItem item) {
            this.jobId = item.getId();
            this.userId = item.getUserId();
            this.fileId = item.getFile().getFileId();
            this.fileSize = item.getFile().getSize();
            this.format = item.getType();
            this.unzipDevice = getCandidate(item.getType());
            this.messageId = item.getMessageId();
        }

        @Override
        public void execute() {
            String size = MemoryUtils.humanReadableByteCount(fileSize);
            LOGGER.debug("Start({}, {}, {}, {})", userId, size, format, fileId);
            in = fileService.createTempFile(userId, fileId, TAG, format.getExt());
            fileManager.forceDownloadFileByFileId(fileId, fileSize, unzipProgress(userId, jobId, messageId), in);
            UnzipState unzipState = initAndGetState(in.getAbsolutePath());
            if (unzipState == null) {
                return;
            }
            Locale locale = userService.getLocaleOrDefault(userId);
            UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, 0, locale);

            messageService.editMessage(new EditMessageText(userId, messageId, messageBuilder.buildUnzipProgressMessage(UnzipStep.COMPLETED, Lang.JAVA, locale)));
            messageService.sendMessage(new SendMessage((long) userId, filesList.getMessage())
                    .setReplyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), jobId, locale)));
            commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);

            LOGGER.debug("Finish({}, {}, {})", userId, size, format);
        }

        @Override
        public void cancel() {
            if (!fileManager.cancelDownloading(fileId) && in != null) {
                in.smartDelete();
            }
            commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);
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
            UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState == null) {
                return null;
            }
            unzipState.setArchivePath(zipFile);
            unzipState.setUnzipJobId(jobId);
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
                    String message = messageBuilder.buildExtractAllProgressMessage(unzipState.getFiles().size(), i + 1, ExtractFileStep.EXTRACTING, Lang.JAVA, locale);
                    String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
                    messageService.editMessage(new EditMessageText(item.getUserId(), item.getMessageId(), String.format(message, 50, "7 " + seconds))
                            .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(item.getId(), locale)));
                } else {
                    SmartTempFile file = fileService.createTempFile(item.getUserId(), TAG, FilenameUtils.getExtension(entry.getValue().getPath()));
                    files.add(file);
                    unzipDevice.unzip(entry.getValue().getPath(), unzipState.getArchivePath(), file.getAbsolutePath());

                    String fileName = FilenameUtils.getName(entry.getValue().getPath());
                    SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) item.getUserId(), fileName, file.getFile())
                            .setProgress(extractAllProgress(unzipState.getFiles().size(), i, item.getUserId(), item.getId(), item.getMessageId(), file.length()))
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
            finishExtracting(item.getUserId(), item.getMessageId(), unzipState);
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

        private int jobId;

        private int id;

        private int userId;

        private int messageId;

        private long fileSize;

        private volatile SmartTempFile out;

        private ExtractFileTask(UnzipQueueItem item) {
            this.jobId = item.getId();
            this.id = item.getExtractFileId();
            this.userId = item.getUserId();
            this.fileSize = item.getExtractFileSize();
            this.messageId = item.getMessageId();
        }

        @Override
        public void execute() {
            String size;

            UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            ZipFileHeader fileHeader = unzipState.getFiles().get(id);
            size = MemoryUtils.humanReadableByteCount(fileHeader.getSize());
            LOGGER.debug("Start({}, {})", userId, size);

            UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());
            out = fileService.createTempFile(userId, TAG, FilenameUtils.getExtension(fileHeader.getPath()));
            unzipDevice.unzip(fileHeader.getPath(), unzipState.getArchivePath(), out.getAbsolutePath());

            String fileName = FilenameUtils.getName(fileHeader.getPath());
            SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) userId, fileName, out.getFile())
                    .setProgress(extractFileProgress(userId, jobId, messageId, fileSize))
                    .setCaption(fileName));
            if (result != null) {
                unzipState.getFilesCache().put(id, result.getFileId());
                commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);
            }
            LOGGER.debug("Finish({}, {})", userId, size);
        }

        @Override
        public void finish() {
            if (out != null) {
                out.smartDelete();
            }
            UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
            finishExtracting(userId, messageId, unzipState);
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
    }
}
