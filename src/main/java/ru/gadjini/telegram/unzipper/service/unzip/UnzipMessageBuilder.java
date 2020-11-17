package ru.gadjini.telegram.unzipper.service.unzip;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.domain.QueueItem;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.message.TgLimitsMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.update.UpdateQueryStatusCommandMessageProvider;
import ru.gadjini.telegram.smart.bot.commons.utils.MemoryUtils;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Service
public class UnzipMessageBuilder implements UpdateQueryStatusCommandMessageProvider {

    private static final int MAX_FILES_IN_MESSAGE = 20;

    private LocalisationService localisationService;

    @Autowired
    public UnzipMessageBuilder(LocalisationService localisationService) {
        this.localisationService = localisationService;
    }

    public String buildExtractAllProgressMessage(int count, int current, ExtractFileStep extractFileStep, int queuePosition, Locale locale) {
        return localisationService.getMessage(MessagesProperties.MESSAGE_FILE_QUEUED, new Object[]{queuePosition}, locale) + "\n\n" +
                localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACTING_ALL, new Object[]{current - 1, count}, locale) + "\n" +
                buildExtractFileProgressMessage(extractFileStep, locale) + "\n\n" +
                localisationService.getMessage(MessagesProperties.MESSAGE_DONT_SEND_NEW_REQUEST, locale);
    }

    public String buildExtractFileProgressMessage(UnzipQueueItem queueItem, ExtractFileStep extractFileStep, Locale locale) {
        return localisationService.getMessage(MessagesProperties.MESSAGE_FILE_QUEUED, new Object[]{queueItem.getQueuePosition()}, locale) + "\n\n" +
                buildExtractFileProgressMessage(extractFileStep, locale) + "\n\n" +
                localisationService.getMessage(MessagesProperties.MESSAGE_DONT_SEND_NEW_REQUEST, locale);
    }

    private String buildExtractFileProgressMessage(ExtractFileStep extractFileStep, Locale locale) {
        String iconCheck = localisationService.getMessage(MessagesProperties.ICON_CHECK, locale);

        switch (extractFileStep) {
            case WAITING:
                return "<b>" + localisationService.getMessage(MessagesProperties.WAITING_STEP, locale) + "...</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.EXTRACTING_STEP, locale) + "</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UPLOADING_STEP, locale) + "</b>";
            case EXTRACTING:
                return "<b>" + localisationService.getMessage(MessagesProperties.EXTRACTING_STEP, locale) + "...</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UPLOADING_STEP, locale) + "</b>";
            case UPLOADING:
                return "<b>" + localisationService.getMessage(MessagesProperties.EXTRACTING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UPLOADING_STEP, locale) + "...</b>";
            default:
                return "<b>" + localisationService.getMessage(MessagesProperties.EXTRACTING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UPLOADING_STEP, locale) + "</b> " + iconCheck + "\n";
        }
    }

    public String buildUnzipProgressMessage(UnzipQueueItem queueItem, UnzipStep unzipStep, Locale locale) {
        return localisationService.getMessage(MessagesProperties.MESSAGE_FILE_QUEUED, new Object[]{queueItem.getQueuePosition()}, locale) + "\n\n" +
                buildUnzipProgressMessage(unzipStep, locale) + "\n\n" +
                localisationService.getMessage(MessagesProperties.MESSAGE_DONT_SEND_NEW_REQUEST, locale);
    }

    private String buildUnzipProgressMessage(UnzipStep unzipStep, Locale locale) {
        String iconCheck = localisationService.getMessage(MessagesProperties.ICON_CHECK, locale);

        switch (unzipStep) {
            case WAITING:
                return "<b>" + localisationService.getMessage(MessagesProperties.WAITING_STEP, locale) + "...</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.DOWNLOADING_STEP, locale) + "</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UNZIPPING_STEP, locale) + "</b>";
            case DOWNLOADING:
                return "<b>" + localisationService.getMessage(MessagesProperties.DOWNLOADING_STEP, locale) + "...</b>\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UNZIPPING_STEP, locale) + "</b>";
            case UNZIPPING:
                return "<b>" + localisationService.getMessage(MessagesProperties.DOWNLOADING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UNZIPPING_STEP, locale) + "...</b>";
            default:
                return "<b>" + localisationService.getMessage(MessagesProperties.DOWNLOADING_STEP, locale) + "</b> " + iconCheck + "\n" +
                        "<b>" + localisationService.getMessage(MessagesProperties.UNZIPPING_STEP, locale) + " </b>" + iconCheck + "\n";
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

    @Override
    public String getWaitingMessage(QueueItem queueItem, Locale locale) {
        UnzipQueueItem unzipQueueItem = (UnzipQueueItem) queueItem;
        if (unzipQueueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            return buildUnzipProgressMessage(UnzipStep.WAITING, locale);
        } else {
            return buildExtractFileProgressMessage(ExtractFileStep.WAITING, locale);
        }
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
