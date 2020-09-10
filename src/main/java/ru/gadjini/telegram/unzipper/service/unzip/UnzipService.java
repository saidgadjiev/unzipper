package ru.gadjini.telegram.unzipper.service.unzip;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.exception.DownloadCanceledException;
import ru.gadjini.telegram.smart.bot.commons.exception.TaskCanceledException;
import ru.gadjini.telegram.smart.bot.commons.exception.UserException;
import ru.gadjini.telegram.smart.bot.commons.exception.botapi.TelegramApiException;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.MessageMedia;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendDocument;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendMessage;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.updatemessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.AnswerCallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Message;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Progress;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.ProgressManager;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileWorkObject;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MediaMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.progress.Lang;
import ru.gadjini.telegram.unzipper.service.queue.UnzipQueueService;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class UnzipService {

    private static final String TAG = "unz";

    private final Logger LOGGER = LoggerFactory.getLogger(UnzipService.class);

    private Set<UnzipDevice> unzipDevices;

    private LocalisationService localisationService;

    private SmartExecutorService executor;

    private MessageService messageService;

    private MediaMessageService mediaMessageService;

    private FileManager fileManager;

    private TempFileService fileService;

    private UnzipQueueService queueService;

    private UserService userService;

    private CommandStateService commandStateService;

    private InlineKeyboardService inlineKeyboardService;

    private UnzipMessageBuilder messageBuilder;

    private ProgressManager progressManager;

    @Autowired
    public UnzipService(Set<UnzipDevice> unzipDevices, LocalisationService localisationService,
                        @Qualifier("messageLimits") MessageService messageService,
                        @Qualifier("mediaLimits") MediaMessageService mediaMessageService, FileManager fileManager, TempFileService fileService,
                        UnzipQueueService queueService, UserService userService,
                        CommandStateService commandStateService, InlineKeyboardService inlineKeyboardService,
                        UnzipMessageBuilder messageBuilder, ProgressManager progressManager) {
        this.unzipDevices = unzipDevices;
        this.localisationService = localisationService;
        this.messageService = messageService;
        this.mediaMessageService = mediaMessageService;
        this.fileManager = fileManager;
        this.fileService = fileService;
        this.queueService = queueService;
        this.userService = userService;
        this.commandStateService = commandStateService;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageBuilder = messageBuilder;
        this.progressManager = progressManager;
    }

    @PostConstruct
    public void init() {
        queueService.resetProcessing();
        pushTasks(SmartExecutorService.JobWeight.LIGHT);
        pushTasks(SmartExecutorService.JobWeight.HEAVY);
    }

    @Autowired
    public void setExecutor(@Qualifier("unzipTaskExecutor") SmartExecutorService executor) {
        this.executor = executor;
    }

    public void rejectTask(SmartExecutorService.Job job) {
        queueService.setWaiting(job.getId());
        LOGGER.debug("Rejected({}, {})", job.getId(), job.getWeight());
    }

    public SmartExecutorService.Job getTask(SmartExecutorService.JobWeight weight) {
        synchronized (this) {
            UnzipQueueItem peek = queueService.poll(weight);

            if (peek != null) {
                if (peek.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
                    return new UnzipTask(peek);
                } else if (peek.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
                    return new ExtractFileTask(peek);
                } else {
                    return new ExtractAllTask(peek);
                }
            }

            return null;
        }
    }

    public void extractAll(int userId, int messageId, int unzipJobId, String queryId) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else if (unzipState.getUnzipJobId() != unzipJobId) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else {
            UnzipQueueItem item = queueService.createProcessingExtractAllItem(userId, messageId,
                    unzipState.getFiles().values().stream().map(ZipFileHeader::getSize).mapToLong(i -> i).sum());
            sendStartExtractingAllMessage(userId, messageId, item.getId());

            executor.execute(new ExtractAllTask(item));
        }
    }

    public void extractFile(int userId, int messageId, int unzipJobId, int extractFileId, String queryId) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else if (unzipState.getUnzipJobId() != unzipJobId) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else {
            if (unzipState.getFilesCache().containsKey(extractFileId)) {
                messageService.sendAnswerCallbackQuery(
                        new AnswerCallbackQuery(
                                queryId,
                                localisationService.getMessage(MessagesProperties.MESSAGE_UNZIP_PROCESSING_ANSWER, userService.getLocaleOrDefault(userId))
                        )
                );
                String fileName = FilenameUtils.getName(unzipState.getFiles().get(extractFileId).getPath());
                mediaMessageService.sendFile(userId, unzipState.getFilesCache().get(extractFileId), fileName);
            } else {
                UnzipQueueItem item = queueService.createProcessingExtractFileItem(userId, messageId,
                        extractFileId, unzipState.getFiles().get(extractFileId).getSize());
                sendStartExtractingFileMessage(userId, messageId, unzipState.getFiles().get(extractFileId).getSize(), item.getId());
                executor.execute(new ExtractFileTask(item));
            }
        }
    }

    public void nextOrPrev(String queryId, long chatId, int userId, int messageId, int prevLimit, int offset) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else {
            Locale locale = userService.getLocaleOrDefault(userId);
            UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, offset, locale);
            InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(),
                    filesList.getLimit(), prevLimit, offset, unzipState.getUnzipJobId(), locale);

            try {
                messageService.editMessage(new EditMessageText(chatId, messageId, filesList.getMessage())
                        .setThrowEx(true)
                        .setReplyMarkup(filesListKeyboard));
            } catch (Exception e) {
                messageService.sendMessage(new SendMessage((long) userId, filesList.getMessage())
                        .setReplyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), unzipState.getUnzipJobId(), locale)));
                if (!(e instanceof TelegramApiException)) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    public void unzip(int userId, int replyToMessageId, MessageMedia file, Locale locale) {
        checkCandidate(file.getFormat(), locale);
        UnzipQueueItem queueItem = queueService.createProcessingUnzipItem(userId, file);
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
        unzipState.setUnzipJobId(queueItem.getId());
        commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);

        sendStartUnzippingMessage(userId, queueItem.getId(), file.getFileSize(), locale, message -> {
            queueItem.setMessageId(message.getMessageId());
            queueService.setMessageId(queueItem.getId(), message.getMessageId());
            fileManager.setInputFilePending(userId, replyToMessageId, file.getFileId(), file.getFileSize(), TAG);
            executor.execute(new UnzipTask(queueItem));
        });
    }

    public void removeAndCancelCurrentTask(long chatId) {
        UnzipQueueItem unzipQueueItem = queueService.deleteByUserId((int) chatId);
        if (unzipQueueItem != null) {
            if (unzipQueueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
                if (!executor.cancelAndComplete(unzipQueueItem.getId(), true)) {
                    fileManager.fileWorkObject(chatId, unzipQueueItem.getFile().getSize()).stop();
                }
            } else {
                executor.cancelAndComplete(unzipQueueItem.getId(), true);
            }
        }
        UnzipState unzipState = commandStateService.getState(chatId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);

        if (unzipState != null) {
            LOGGER.debug("Remove previous state({})", chatId);
            if (StringUtils.isNotBlank(unzipState.getArchivePath())) {
                new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
            }
            commandStateService.deleteState(chatId, UnzipCommandNames.START_COMMAND_NAME);
        }
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
                UnzipQueueItem unzipQueueItem = queueService.deleteWithReturning(jobId);
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
                queueService.delete(jobId);
            }
            UnzipState unzipState = commandStateService.getState(chatId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null) {
                Locale locale = userService.getLocaleOrDefault((int) chatId);
                UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, 0, locale);
                InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), unzipState.getUnzipJobId(), locale);
                messageService.editMessage(new EditMessageText(chatId, messageId, filesList.getMessage())
                        .setReplyMarkup(filesListKeyboard));
            }
        }
    }

    private void sendStartUnzippingMessage(int userId, int jobId, long fileSize, Locale locale, Consumer<Message> callback) {
        if (progressManager.isShowingDownloadingProgress(fileSize)) {
            String message = localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
            messageService.sendMessage(new SendMessage((long) userId, message)
                    .setReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(jobId, locale)), callback);
        } else {
            String message = localisationService.getMessage(MessagesProperties.MESSAGE_ARCHIVE_PROCESSING, locale);
            messageService.sendMessage(new SendMessage((long) userId, message)
                    .setReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(jobId, locale)), callback);
        }
    }

    private void pushTasks(SmartExecutorService.JobWeight jobWeight) {
        List<UnzipQueueItem> tasks = queueService.poll(jobWeight, 1);
        for (UnzipQueueItem item : tasks) {
            if (item.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
                executor.execute(new UnzipTask(item));
            } else if (item.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
                executor.execute(new ExtractFileTask(item));
            } else {
                executor.execute(new ExtractAllTask(item));
            }
            LOGGER.debug("Push " + item.getItemType() + " task");
        }
    }

    private void sendStartExtractingAllMessage(int userId, int messageId, int jobId) {
        Locale locale = userService.getLocaleOrDefault(userId);
        String message = localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
        messageService.editMessage(new EditMessageText((long) userId, messageId, message)
                .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale)));
    }

    private void sendStartExtractingFileMessage(int userId, int messageId, long fileSize, int jobId) {
        Locale locale = userService.getLocaleOrDefault(userId);
        if (progressManager.isShowingDownloadingProgress(fileSize)) {
            String message = localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);
            messageService.editMessage(new EditMessageText((long) userId, messageId, message)
                    .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale)));
        } else {
            String message = localisationService.getMessage(MessagesProperties.MESSAGE_UNZIP_PROCESSING, locale);
            messageService.editMessage(new EditMessageText((long) userId, messageId, message)
                    .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale)));
        }
    }

    private void finishExtracting(int userId, int messageId, UnzipState unzipState) {
        Locale locale = userService.getLocaleOrDefault(userId);
        UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, 0, locale);
        InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), unzipState.getUnzipJobId(), locale);

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

    private void checkCandidate(Format format, Locale locale) {
        for (UnzipDevice unzipDevice : unzipDevices) {
            if (unzipDevice.accept(format)) {
                return;
            }
        }

        LOGGER.warn("Candidate not found({})", format);
        throw new UserException(localisationService.getMessage(MessagesProperties.MESSAGE_SUPPORTED_ZIP_FORMATS, locale));
    }

    private Progress extractAllProgress(int count, int current, long chatId, int jobId, int processMessageId) {
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
    }

    private Progress extractFileProgress(long chatId, int jobId, int processMessageId) {
        Locale locale = userService.getLocaleOrDefault((int) chatId);
        Progress progress = new Progress();
        progress.setLocale(locale.getLanguage());
        progress.setChatId(chatId);
        progress.setProgressMessageId(processMessageId);
        progress.setProgressMessage(messageBuilder.buildExtractFileProgressMessage(ExtractFileStep.UPLOADING, Lang.PYTHON, locale));
        progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));

        return progress;
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

    public void shutdown() {
        executor.shutdown();
    }

    public class ExtractAllTask implements SmartExecutorService.Job {

        private static final String TAG = "extractall";

        private static final int SLEEP_TIME = 2500;

        private UnzipQueueItem item;

        private Queue<SmartTempFile> files = new LinkedBlockingQueue<>();

        private volatile Supplier<Boolean> checker;

        private volatile boolean canceledByUser;

        private ExtractAllTask(UnzipQueueItem item) {
            this.item = item;
        }

        @Override
        public int getId() {
            return item.getId();
        }

        @Override
        public SmartExecutorService.JobWeight getWeight() {
            return item.getExtractFileSize() > MemoryUtils.MB_100 ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public void execute() {
            String size = MemoryUtils.humanReadableByteCount(item.getExtractFileSize());
            LOGGER.debug("Start extract all({}, {})", item.getUserId(), size);

            try {
                UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);

                try {
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
                                    .setProgress(extractAllProgress(unzipState.getFiles().size(), i, item.getUserId(), item.getId(), item.getMessageId()))
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
                } catch (InterruptedException e) {
                    throw new TaskCanceledException(e);
                } finally {
                    if (checker == null || !checker.get()) {
                        finishExtracting(item.getUserId(), item.getMessageId(), unzipState);
                    }
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(item.getId());
                    queueService.delete(item.getId());
                    files.forEach(SmartTempFile::smartDelete);
                }
            }
        }

        @Override
        public void setCancelChecker(Supplier<Boolean> checker) {
            this.checker = checker;
        }

        @Override
        public Supplier<Boolean> getCancelChecker() {
            return checker;
        }

        @Override
        public void setCanceledByUser(boolean canceledByUser) {
            this.canceledByUser = canceledByUser;
        }

        @Override
        public void cancel() {
            if (canceledByUser) {
                queueService.delete(item.getId());
                LOGGER.debug("Extracting canceled by user({}, {})", item.getUserId(), MemoryUtils.humanReadableByteCount(item.getExtractFileSize()));
            }
            files.forEach(smartTempFile -> {
                if (!fileManager.cancelUploading(smartTempFile.getAbsolutePath())) {
                    smartTempFile.smartDelete();
                }
            });
        }

        @Override
        public int getProgressMessageId() {
            return item.getMessageId();
        }

        @Override
        public String getErrorCode(Exception e) {
            return MessagesProperties.MESSAGE_UNZIP_ERROR;
        }

        @Override
        public long getChatId() {
            return item.getUserId();
        }
    }

    public class ExtractFileTask implements SmartExecutorService.Job {

        private static final String TAG = "extractfile";

        private final Logger LOGGER = LoggerFactory.getLogger(ExtractFileTask.class);

        private int jobId;

        private int id;

        private int userId;

        private int messageId;

        private long fileSize;

        private volatile Supplier<Boolean> checker;

        private volatile boolean canceledByUser;

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

            try {
                UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
                try {
                    ZipFileHeader fileHeader = unzipState.getFiles().get(id);
                    size = MemoryUtils.humanReadableByteCount(fileHeader.getSize());
                    LOGGER.debug("Start({}, {})", userId, size);

                    UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());
                    out = fileService.createTempFile(userId, TAG, FilenameUtils.getExtension(fileHeader.getPath()));
                    unzipDevice.unzip(fileHeader.getPath(), unzipState.getArchivePath(), out.getAbsolutePath());

                    String fileName = FilenameUtils.getName(fileHeader.getPath());
                    SendFileResult result = mediaMessageService.sendDocument(new SendDocument((long) userId, fileName, out.getFile())
                            .setProgress(extractFileProgress(userId, jobId, messageId))
                            .setCaption(fileName));
                    if (result != null) {
                        unzipState.getFilesCache().put(id, result.getFileId());
                        commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);
                    }
                    LOGGER.debug("Finish({}, {})", userId, size);
                } finally {
                    if (checker == null || !checker.get()) {
                        finishExtracting(userId, messageId, unzipState);
                    }
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(jobId);
                    queueService.delete(jobId);
                    if (out != null) {
                        out.smartDelete();
                    }
                }
            }
        }

        @Override
        public int getId() {
            return jobId;
        }

        @Override
        public void setCancelChecker(Supplier<Boolean> checker) {
            this.checker = checker;
        }

        @Override
        public Supplier<Boolean> getCancelChecker() {
            return checker;
        }

        @Override
        public void setCanceledByUser(boolean canceledByUser) {
            this.canceledByUser = canceledByUser;
        }

        @Override
        public void cancel() {
            if (canceledByUser) {
                queueService.delete(jobId);
                LOGGER.debug("Extracting canceled by user({}, {})", userId, MemoryUtils.humanReadableByteCount(fileSize));
            }
            if (out != null && !fileManager.cancelUploading(out.getAbsolutePath())) {
                out.smartDelete();
            }
        }

        @Override
        public SmartExecutorService.JobWeight getWeight() {
            return fileSize > MemoryUtils.MB_100 ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public int getProgressMessageId() {
            return messageId;
        }

        @Override
        public String getErrorCode(Exception e) {
            return MessagesProperties.MESSAGE_EXTRACT_FILE_ERROR;
        }

        @Override
        public long getChatId() {
            return userId;
        }
    }

    public class UnzipTask implements SmartExecutorService.Job {

        private static final String TAG = "unzip";

        private final Logger LOGGER = LoggerFactory.getLogger(UnzipTask.class);

        private final int jobId;
        private final int userId;
        private final String fileId;
        private final long fileSize;
        private final Format format;
        private final int messageId;
        private UnzipDevice unzipDevice;
        private volatile Supplier<Boolean> checker;
        private volatile boolean canceledByUser;
        private volatile SmartTempFile in;
        private FileWorkObject fileWorkObject;

        private UnzipTask(UnzipQueueItem item) {
            this.jobId = item.getId();
            this.userId = item.getUserId();
            this.fileId = item.getFile().getFileId();
            this.fileSize = item.getFile().getSize();
            this.format = item.getType();
            this.unzipDevice = getCandidate(item.getType());
            this.messageId = item.getMessageId();
            this.fileWorkObject = fileManager.fileWorkObject(userId, fileSize);
        }

        @Override
        public void execute() {
            fileWorkObject.start();
            String size = MemoryUtils.humanReadableByteCount(fileSize);
            LOGGER.debug("Start({}, {}, {}, {})", userId, size, format, fileId);

            try {
                in = fileService.createTempFile(userId, fileId, TAG, format.getExt());
                fileManager.downloadFileByFileId(fileId, fileSize, unzipProgress(userId, jobId, messageId), in);
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
            } catch (Exception e) {
                if (checker == null || !checker.get() || ExceptionUtils.indexOfThrowable(e, DownloadCanceledException.class) == -1) {
                    if (in != null) {
                        in.smartDelete();
                    }
                    commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);

                    throw e;
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(jobId);
                    queueService.delete(jobId);
                    fileWorkObject.stop();
                }
            }
        }

        @Override
        public int getId() {
            return jobId;
        }

        @Override
        public void setCancelChecker(Supplier<Boolean> checker) {
            this.checker = checker;
        }

        @Override
        public Supplier<Boolean> getCancelChecker() {
            return checker;
        }

        @Override
        public void cancel() {
            if (!fileManager.cancelDownloading(fileId) && in != null) {
                in.smartDelete();
            }
            if (canceledByUser) {
                queueService.delete(jobId);
                LOGGER.debug("Unzip canceled by user({}, {})", userId, MemoryUtils.humanReadableByteCount(fileSize));
            }
            commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);
            fileWorkObject.stop();
        }

        @Override
        public void setCanceledByUser(boolean canceledByUser) {
            this.canceledByUser = canceledByUser;
        }

        @Override
        public SmartExecutorService.JobWeight getWeight() {
            return fileSize > MemoryUtils.MB_100 ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public int getProgressMessageId() {
            return messageId;
        }

        @Override
        public String getErrorCode(Exception e) {
            return MessagesProperties.MESSAGE_UNZIP_ERROR;
        }

        @Override
        public long getChatId() {
            return userId;
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
}
