package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.exception.DownloadCanceledException;
import ru.gadjini.telegram.smart.bot.commons.exception.ProcessException;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendDocument;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendMessage;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.updatemessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.AnswerCallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Progress;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.property.FileLimitProperties;
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
import ru.gadjini.telegram.unzipper.service.unzip.*;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

@Component
public class UnzipperJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnzipperJob.class);

    private SmartExecutorService executor;

    private UnzipQueueService queueService;

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

    private FileLimitProperties fileLimitProperties;

    private ProgressManager progressManager;

    @Autowired
    public UnzipperJob(UnzipQueueService queueService, Set<UnzipDevice> unzipDevices,
                       LocalisationService localisationService, @Qualifier("messageLimits") MessageService messageService,
                       @Qualifier("forceMedia") MediaMessageService mediaMessageService, FileManager fileManager,
                       TempFileService fileService, UserService userService, CommandStateService commandStateService,
                       InlineKeyboardService inlineKeyboardService, UnzipMessageBuilder messageBuilder, FileLimitProperties fileLimitProperties, ProgressManager progressManager) {
        this.queueService = queueService;
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
        this.fileLimitProperties = fileLimitProperties;
        this.progressManager = progressManager;
    }

    @Autowired
    public void setExecutor(@Qualifier("unzipTaskExecutor") SmartExecutorService executor) {
        this.executor = executor;
    }

    @PostConstruct
    public void init() {
        queueService.resetProcessing();
    }

    @Scheduled(fixedDelay = 1000)
    public void pushTasks() {
        ThreadPoolExecutor heavyExecutor = executor.getExecutor(SmartExecutorService.JobWeight.HEAVY);
        if (heavyExecutor.getActiveCount() < heavyExecutor.getCorePoolSize()) {
            Collection<UnzipQueueItem> items = queueService.poll(SmartExecutorService.JobWeight.HEAVY, heavyExecutor.getCorePoolSize() - heavyExecutor.getActiveCount());

            if (items.size() > 0) {
                LOGGER.debug("Push heavy jobs({})", items.size());
            }
            items.forEach(queueItem -> executor.execute(toTask(queueItem)));
        }
        ThreadPoolExecutor lightExecutor = executor.getExecutor(SmartExecutorService.JobWeight.LIGHT);
        if (lightExecutor.getActiveCount() < lightExecutor.getCorePoolSize()) {
            Collection<UnzipQueueItem> items = queueService.poll(SmartExecutorService.JobWeight.LIGHT, lightExecutor.getCorePoolSize() - lightExecutor.getActiveCount());

            if (items.size() > 0) {
                LOGGER.debug("Push light jobs({})", items.size());
            }
            items.forEach(queueItem -> executor.execute(toTask(queueItem)));
        }
        if (heavyExecutor.getActiveCount() < heavyExecutor.getCorePoolSize()) {
            Collection<UnzipQueueItem> items = queueService.poll(SmartExecutorService.JobWeight.LIGHT, heavyExecutor.getCorePoolSize() - heavyExecutor.getActiveCount());

            if (items.size() > 0) {
                LOGGER.debug("Push light jobs to heavy threads({})", items.size());
            }
            items.forEach(queueItem -> executor.execute(toTask(queueItem), SmartExecutorService.JobWeight.HEAVY));
        }
    }

    private SmartExecutorService.Job toTask(UnzipQueueItem item) {
        if (item.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            return new UnzipTask(item);
        } else if (item.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            return new ExtractFileTask(item);
        } else {
            return new ExtractAllTask(item);
        }
    }

    public void rejectTask(SmartExecutorService.Job job) {
        queueService.setWaiting(job.getId());
        LOGGER.debug("Rejected({}, {})", job.getId(), job.getWeight());
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
                UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
                InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);
                messageService.editMessage(new EditMessageText(chatId, messageId, filesList.getMessage())
                        .setReplyMarkup(filesListKeyboard));
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void handleDownloadingUploadingException(Throwable e, SmartExecutorService.Job job) {
        LOGGER.error(e.getMessage());
        queueService.setWaiting(job.getId());
        updateProgressMessageAfterFloodWait(job.getChatId(), job.getProgressMessageId(), job.getId());
    }

    private void updateProgressMessageAfterFloodWait(long chatId, int progressMessageId, int id) {
        Locale locale = userService.getLocaleOrDefault(id);
        String message = localisationService.getMessage(MessagesProperties.MESSAGE_AWAITING_PROCESSING, locale);

        messageService.editMessage(new EditMessageText(chatId, progressMessageId, message)
                .setNoLogging(true)
                .setReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(id, locale)));
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
            boolean success = false;
            try {
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

                success = true;
                LOGGER.debug("Finish({}, {}, {})", userId, size, format);
            } catch (Throwable e) {
                if (checker == null || !checker.get() || ExceptionUtils.indexOfThrowable(e, DownloadCanceledException.class) == -1) {
                    if (FileManager.isSomethingWentWrongWithDownloadingUploading(e)) {
                        handleDownloadingUploadingException(e, this);
                    } else {
                        if (in != null) {
                            in.smartDelete();
                        }
                        commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);
                        throw e;
                    }
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(jobId);
                    if (success) {
                        queueService.delete(jobId);
                        fileWorkObject.stop();
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
            return fileSize > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public int getProgressMessageId() {
            return messageId;
        }

        @Override
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_UNZIP_ERROR;
            }

            return null;
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
            return item.getExtractFileSize() > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public void execute() throws Exception {
            String size = MemoryUtils.humanReadableByteCount(item.getExtractFileSize());
            LOGGER.debug("Start extract all({}, {})", item.getUserId(), size);
            boolean success = false;

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
                    success = true;
                    LOGGER.debug("Finish extract all({}, {})", item.getUserId(), size);
                } finally {
                    if (checker == null || !checker.get()) {
                        finishExtracting(item.getUserId(), item.getMessageId(), unzipState);
                    }
                }
            } catch (Throwable e) {
                if (checker == null || !checker.get() || ExceptionUtils.indexOfThrowable(e, DownloadCanceledException.class) == -1) {
                    if (FileManager.isSomethingWentWrongWithDownloadingUploading(e)) {
                        handleDownloadingUploadingException(e, this);
                    } else {
                        throw e;
                    }
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(item.getId());
                    files.forEach(SmartTempFile::smartDelete);
                    if (success) {
                        queueService.delete(item.getId());
                    }
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
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_UNZIP_ERROR;
            }

            return null;
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

            boolean success = false;
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
                            .setProgress(extractFileProgress(userId, jobId, messageId, fileSize))
                            .setCaption(fileName));
                    if (result != null) {
                        unzipState.getFilesCache().put(id, result.getFileId());
                        commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);
                    }
                    success = true;
                    LOGGER.debug("Finish({}, {})", userId, size);
                } finally {
                    if (checker == null || !checker.get()) {
                        finishExtracting(userId, messageId, unzipState);
                    }
                }
            } catch (Throwable e) {
                if (checker == null || !checker.get() || ExceptionUtils.indexOfThrowable(e, DownloadCanceledException.class) == -1) {
                    if (FileManager.isSomethingWentWrongWithDownloadingUploading(e)) {
                        handleDownloadingUploadingException(e, this);
                    } else {
                        throw e;
                    }
                }
            } finally {
                if (checker == null || !checker.get()) {
                    executor.complete(jobId);
                    if (success) {
                        queueService.delete(jobId);
                    }
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
            return fileSize > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
        }

        @Override
        public int getProgressMessageId() {
            return messageId;
        }

        @Override
        public String getErrorCode(Throwable e) {
            if (e instanceof ProcessException) {
                return MessagesProperties.MESSAGE_EXTRACT_FILE_ERROR;
            }

            return null;
        }

        @Override
        public long getChatId() {
            return userId;
        }
    }
}
