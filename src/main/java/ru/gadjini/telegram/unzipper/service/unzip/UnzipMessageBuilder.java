package ru.gadjini.telegram.unzipper.service.unzip;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.ProgressManager;
import ru.gadjini.telegram.smart.bot.commons.service.message.TgLimitsMessageService;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.progress.Lang;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Service
public class UnzipMessageBuilder {

    private static final int MAX_FILES_IN_MESSAGE = 20;

    private LocalisationService localisationService;

    private ProgressManager progressManager;

    @Autowired
    public UnzipMessageBuilder(LocalisationService localisationService, ProgressManager progressManager) {
        this.localisationService = localisationService;
        this.progressManager = progressManager;
    }

    public String buildExtractAllProgressMessage(int count, int current, ExtractFileStep extractFileStep, long fileSize, Lang lang, Locale locale) {
        String msg = localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACTING_ALL, new Object[]{current - 1, count}, locale);

        return msg + "\n" + buildExtractFileProgressMessage(extractFileStep, fileSize, lang, locale);
    }

    public String buildExtractFileProgressMessage(UnzipQueueItem queueItem, ExtractFileStep extractFileStep, Lang lang, Locale locale) {
        StringBuilder message = new StringBuilder();

        message.append(localisationService.getMessage(MessagesProperties.MESSAGE_FILE_QUEUED, new Object[]{queueItem.getQueuePosition()}, locale)).append("\n\n");

        message.append(buildExtractFileProgressMessage(extractFileStep, queueItem.getSize(), lang, locale));

        return message.toString();
    }

    private String buildExtractFileProgressMessage(ExtractFileStep extractFileStep, long fileSize, Lang lang, Locale locale) {
        String formatter = lang == Lang.JAVA ? "%s" : "{}";
        String percentage = lang == Lang.JAVA ? "%%" : "%";
        String iconCheck = localisationService.getMessage(MessagesProperties.ICON_CHECK, locale);
        boolean progress = isShowingProgress(fileSize, extractFileStep);
        String percentageFormatter = progress ? "(" + formatter + percentage + ")..." : "...";

        switch (extractFileStep) {
            case EXTRACTING:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACTING_STEP, locale) + " " + percentageFormatter + "...</b>\n" +
                        (progress ? localisationService.getMessage(MessagesProperties.MESSAGE_ETA, locale) + " <b>" + formatter + "</b>\n" : "") +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UPLOADING_STEP, locale) + "</b>";
            case UPLOADING:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACTING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UPLOADING_STEP, locale) + " " + percentageFormatter + "...</b>\n" +
                        (progress ? localisationService.getMessage(MessagesProperties.MESSAGE_ETA, locale) + " <b>" + formatter + "</b>\n" : "") +
                        (progress ? localisationService.getMessage(MessagesProperties.MESSAGE_SPEED, locale) + " <b>" + formatter + "</b>" : "");
            default:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACTING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UPLOADING_STEP, locale) + "</b> " + iconCheck + "\n";
        }
    }

    public String buildUnzipProgressMessage(UnzipQueueItem queueItem, UnzipStep unzipStep, Lang lang, Locale locale) {
        StringBuilder message = new StringBuilder();

        message.append(localisationService.getMessage(MessagesProperties.MESSAGE_FILE_QUEUED, new Object[]{queueItem.getQueuePosition()}, locale)).append("\n\n");

        message.append(buildUnzipProgressMessage(unzipStep, queueItem.getSize(), lang, locale));

        return message.toString();
    }

    private String buildUnzipProgressMessage(UnzipStep unzipStep, long fileSize, Lang lang, Locale locale) {
        String formatter = lang == Lang.JAVA ? "%s" : "{}";
        String percentage = lang == Lang.JAVA ? "%%" : "%";
        String iconCheck = localisationService.getMessage(MessagesProperties.ICON_CHECK, locale);
        boolean progress = isShowingProgress(fileSize, unzipStep);
        String percentageFormatter = progress ? "(" + formatter + percentage + ")..." : "...";

        switch (unzipStep) {
            case DOWNLOADING:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_DOWNLOADING_STEP, locale) + " " + percentageFormatter + "...</b>\n" +
                        (progress ? localisationService.getMessage(MessagesProperties.MESSAGE_ETA, locale) + " <b>" + formatter + "</b>\n" : "") +
                        (progress ? localisationService.getMessage(MessagesProperties.MESSAGE_SPEED, locale) + " <b>" + formatter + "</b>\n" : "") +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UNZIPPING_STEP, locale) + "</b>";
            case UNZIPPING:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_DOWNLOADING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UNZIPPING_STEP, locale) + " " + percentageFormatter + "...</b>\n" +
                        localisationService.getMessage(MessagesProperties.MESSAGE_ETA, locale) + " " + formatter + "\n";
            default:
                return "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_DOWNLOADING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.MESSAGE_UNZIPPING_STEP, locale) + " </b>" + iconCheck + "\n";
        }
    }

    public FilesMessage getFilesList(Map<Integer, ZipFileHeader> files, int limit, int offset, Locale locale) {
        StringBuilder message = new StringBuilder();

        for (Iterator<Map.Entry<Integer, ZipFileHeader>> iterator = files.entrySet().stream().skip(offset).iterator(); iterator.hasNext(); ) {
            Map.Entry<Integer, ZipFileHeader> entry = iterator.next();
            StringBuilder fileHeaderStr = new StringBuilder();
            fileHeaderStr.append(entry.getKey()).append(") ").append(entry.getValue().getPath());
            if (entry.getValue().getSize() != 0) {
                fileHeaderStr.append(" (").append(MemoryUtils.humanReadableByteCount(entry.getValue().getSize())).append(")");
            }
            String finalMessage = localisationService.getMessage(
                    MessagesProperties.MESSAGE_ARCHIVE_FILES_LIST,
                    new Object[]{message.toString() + fileHeaderStr.toString()},
                    locale
            );
            if (limit >= MAX_FILES_IN_MESSAGE) {
                return new FilesMessage(localisationService.getMessage(
                        MessagesProperties.MESSAGE_ARCHIVE_FILES_LIST,
                        new Object[]{message.toString()},
                        locale
                ), limit, offset);
            } else if (finalMessage.length() > TgLimitsMessageService.TEXT_LENGTH_LIMIT) {
                return new FilesMessage(localisationService.getMessage(
                        MessagesProperties.MESSAGE_ARCHIVE_FILES_LIST,
                        new Object[]{message.toString()},
                        locale
                ), limit, offset);
            } else {
                message.append(fileHeaderStr);
            }
            if (iterator.hasNext()) {
                message.append("\n");
            }
            ++limit;
        }

        return new FilesMessage(localisationService.getMessage(
                MessagesProperties.MESSAGE_ARCHIVE_FILES_LIST,
                new Object[]{message.toString()},
                locale
        ), limit, offset);
    }

    private boolean isShowingProgress(long fileSize, ExtractFileStep extractFileStep) {
        if (extractFileStep == ExtractFileStep.UPLOADING) {
            return progressManager.isShowingUploadingProgress(fileSize);
        }

        return false;
    }

    private boolean isShowingProgress(long fileSize, UnzipStep unzipStep) {
        if (unzipStep == UnzipStep.DOWNLOADING) {
            return progressManager.isShowingDownloadingProgress(fileSize);
        }

        return false;
    }

    public static class FilesMessage {

        private String message;

        private int limit;

        private int offset;

        private FilesMessage(String message, int limit, int offset) {
            this.message = message;
            this.limit = limit;
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public String getMessage() {
            return message;
        }

        public int getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return "FilesMessage{" +
                    "message='\n" + message + '\'' +
                    ", limit=" + limit +
                    ", offset=" + offset +
                    '}';
        }
    }
}
