package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.domain.UploadQueueItem;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.Progress;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileUploadService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MediaMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.unzip.ExtractFileStep;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipDevice;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipMessageBuilder;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.*;

@Component
public class ArchiveExtractor {

    private static final String TAG = "extract";

    private Set<UnzipDevice> unzipDevices;

    private UserService userService;

    private UnzipMessageBuilder messageBuilder;

    private InlineKeyboardService inlineKeyboardService;

    private MediaMessageService mediaMessageService;

    private MessageService messageService;

    private FileUploadService fileUploadService;

    private LocalisationService localisationService;

    private TempFileService fileService;

    @Autowired
    public ArchiveExtractor(Set<UnzipDevice> unzipDevices, UserService userService, UnzipMessageBuilder messageBuilder,
                            InlineKeyboardService inlineKeyboardService, @Qualifier("messageLimits") MessageService messageService,
                            @Qualifier("mediaLimits") MediaMessageService mediaMessageService,
                            LocalisationService localisationService, TempFileService fileService) {
        this.unzipDevices = unzipDevices;
        this.userService = userService;
        this.messageBuilder = messageBuilder;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageService = messageService;
        this.mediaMessageService = mediaMessageService;
        this.localisationService = localisationService;
        this.fileService = fileService;
    }

    @Autowired
    public void setFileUploadService(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    public ListIterator<ArchiveFile> getIterator(Extra extra, Map<Integer, ZipFileHeader> files) {
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        for (Map.Entry<Integer, ZipFileHeader> entry : files.entrySet()) {
            archiveFiles.add(new ArchiveFile(entry));
        }
        return archiveFiles.listIterator(extra.getLastFileIndex() + 1);
    }

    public ListIterator<ArchiveFile> getIterator(int startWith, Map<Integer, ZipFileHeader> files) {
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        for (Map.Entry<Integer, ZipFileHeader> entry : files.entrySet()) {
            archiveFiles.add(new ArchiveFile(entry));
        }
        return archiveFiles.listIterator(startWith);
    }

    public void sendExtractedFile(UploadQueueItem item, Extra currentExtra, ExtractedFile file) {
        if (file.getFile().isNew()) {
            sendNewExtractedFile(item.getUserId(), item.getProducerId(), currentExtra.getProgressMessageId(),
                    currentExtra.getQueuePosition(), file, createNextExtra(currentExtra, file));
        } else {
            sendFromCache(item.getUserId(), item.getProducerId(), currentExtra.getProgressMessageId(), currentExtra.getQueuePosition(), file);
        }
    }

    public void sendExtractedFile(UnzipQueueItem item, ExtractedFile file) {
        if (file.getFile().isNew()) {
            sendNewExtractedFile(item.getUserId(), item.getId(), item.getProgressMessageId(), item.getQueuePosition(), file, createNextExtra(item, file));
        } else {
            sendFromCache(item.getUserId(), item.getId(), item.getProgressMessageId(), item.getQueuePosition(), file);
        }
    }

    public void finishExtracting(int userId, int progressMessageId, UnzipState unzipState) {
        Locale locale = userService.getLocaleOrDefault(userId);

        UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
        InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);

        messageService.editMessage(EditMessageText.builder().chatId(String.valueOf(userId))
                .messageId(progressMessageId)
                .text(filesList.getMessage())
                .replyMarkup(filesListKeyboard).build(), false);
    }

    private Extra createNextExtra(UnzipQueueItem item, ExtractedFile file) {
        return new Extra(
                item.getItemType(),
                file.getCacheKey(),
                item.getProgressMessageId(),
                file.getIndex(),
                item.getQueuePosition()
        );
    }

    private Extra createNextExtra(Extra extra, ExtractedFile file) {
        return new Extra(
                extra.getItemType(),
                file.getCacheKey(),
                extra.getProgressMessageId(),
                file.getIndex(),
                extra.getQueuePosition()
        );
    }

    private Progress extractAllProgress(int userId, int progressMessageId, int queuePosition, int jobId, int count, int extractedCount) {
        Locale locale = userService.getLocaleOrDefault(userId);
        Progress progress = new Progress();
        progress.setChatId(userId);
        progress.setProgressMessageId(progressMessageId);
        progress.setProgressMessage(messageBuilder.buildExtractAllProgressMessage(count, extractedCount, ExtractFileStep.UPLOADING,
                queuePosition, locale));

        if (extractedCount < count) {
            String completionMessage = messageBuilder.buildExtractAllProgressMessage(count, extractedCount + 1, ExtractFileStep.EXTRACTING,
                    queuePosition, locale);
            String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
            progress.setAfterProgressCompletionMessage(String.format(completionMessage, 50, "10 " + seconds));
            progress.setAfterProgressCompletionReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));
        }
        progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));

        return progress;
    }

    private Progress extractFileProgress(int userId, int jobId, int progressMessageId, int queuePosition) {
        Locale locale = userService.getLocaleOrDefault(userId);
        Progress progress = new Progress();
        progress.setChatId(userId);
        progress.setProgressMessageId(progressMessageId);
        progress.setProgressMessage(messageBuilder.buildExtractFileProgressMessage(queuePosition, ExtractFileStep.UPLOADING, locale));
        progress.setProgressReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale));

        return progress;
    }

    private void sendNewExtractedFile(int userId, int jobId, int progressMessageId, int queuePosition, ExtractedFile file, Extra nextExtra) {
        SendDocument sendDocument = SendDocument.builder().chatId(String.valueOf(userId))
                .document(file.getFile())
                .caption(file.getFile().getMediaName()).build();
        fileUploadService.createUpload(userId, SendDocument.PATH, sendDocument,
                nextExtra.getItemType() == UnzipQueueItem.ItemType.EXTRACT_ALL
                        ? extractAllProgress(userId, progressMessageId, queuePosition, jobId, file.getTotalFiles(), file.getIndex())
                        : extractFileProgress(userId, jobId, progressMessageId, queuePosition), jobId, nextExtra);
    }

    private void sendFromCache(int userId, int jobId, int progressMessageId, int queuePosition, ExtractedFile extractedFile) {
        Locale locale = userService.getLocaleOrDefault(userId);
        mediaMessageService.sendFile(userId, extractedFile.getFile().getAttachName());
        String message = messageBuilder.buildExtractAllProgressMessage(extractedFile.getTotalFiles(), extractedFile.getIndex() + 1,
                ExtractFileStep.EXTRACTING, queuePosition, locale);
        String seconds = localisationService.getMessage(MessagesProperties.SECOND_PART, locale);
        messageService.editMessage(EditMessageText.builder().chatId(String.valueOf(userId))
                .messageId(progressMessageId)
                .text(String.format(message, 50, "7 " + seconds))
                .replyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(jobId, locale))
                .build());
    }

    private UnzipDevice getCandidate(Format format) {
        for (UnzipDevice unzipDevice : unzipDevices) {
            if (unzipDevice.accept(format)) {
                return unzipDevice;
            }
        }

        throw new IllegalArgumentException("Candidate not found for " + format + ". Wtf?");
    }

    public class ArchiveFile {

        private Map.Entry<Integer, ZipFileHeader> archiveFileEntry;

        private ArchiveFile(Map.Entry<Integer, ZipFileHeader> archiveFileEntry) {
            this.archiveFileEntry = archiveFileEntry;
        }

        public ExtractedFile extract(int userId, UnzipState unzipState) {
            if (unzipState.getFilesCache().containsKey(archiveFileEntry.getKey())) {
                return new ExtractedFile(new InputFile(unzipState.getFilesCache().get(archiveFileEntry.getKey())), archiveFileEntry.getKey(), unzipState.getFiles().size(), true);
            } else {
                SmartTempFile file = fileService.createTempFile(userId, TAG, FilenameUtils.getExtension(archiveFileEntry.getValue().getPath()));
                UnzipDevice unzipDevice = getCandidate(unzipState.getArchiveType());
                unzipDevice.unzip(archiveFileEntry.getValue().getPath(), unzipState.getArchivePath(), file.getAbsolutePath(), unzipState.getPassword());

                String fileName = FilenameUtils.getName(archiveFileEntry.getValue().getPath());

                return new ExtractedFile(new InputFile(file.getFile(), fileName), archiveFileEntry.getKey(), unzipState.getFiles().size(), false);
            }
        }
    }

    public static class ExtractedFile {

        private InputFile file;

        private int cacheKey;

        private int totalFiles;

        private boolean fromCache;

        private ExtractedFile(InputFile file, int cacheKey, int totalFiles, boolean fromCache) {
            this.file = file;
            this.cacheKey = cacheKey;
            this.totalFiles = totalFiles;
            this.fromCache = fromCache;
        }

        public boolean isNewMedia() {
            return !isFromCache();
        }

        public InputFile getFile() {
            return file;
        }

        public int getCacheKey() {
            return cacheKey;
        }

        public int getIndex() {
            return cacheKey - 1;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public boolean isFromCache() {
            return fromCache;
        }
    }
}
