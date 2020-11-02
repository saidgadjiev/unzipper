package ru.gadjini.telegram.unzipper.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.exception.ProcessException;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueJobConfigurator;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.progress.Lang;
import ru.gadjini.telegram.unzipper.service.unzip.ExtractFileStep;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipMessageBuilder;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipStep;

import java.util.Locale;

@Component
public class UnzipQueueJobConfigurator implements QueueJobConfigurator<UnzipQueueItem> {

    private UnzipMessageBuilder messageBuilder;

    private InlineKeyboardService inlineKeyboardService;

    @Autowired
    public UnzipQueueJobConfigurator(UnzipMessageBuilder messageBuilder, InlineKeyboardService inlineKeyboardService) {
        this.messageBuilder = messageBuilder;
        this.inlineKeyboardService = inlineKeyboardService;
    }

    @Override
    public boolean isNeedUpdateMessageAfterCancel(UnzipQueueItem queueItem) {
        return queueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP;
    }

    @Override
    public String getErrorCode(Throwable e) {
        if (e instanceof ProcessException) {
            return MessagesProperties.MESSAGE_UNZIP_ERROR;
        }

        return null;
    }

    @Override
    public String getWaitingMessage(UnzipQueueItem queueItem, Locale locale) {
        if (queueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            return messageBuilder.buildUnzipProgressMessage(queueItem, UnzipStep.WAITING, Lang.JAVA, locale);
        } else {
            return messageBuilder.buildExtractFileProgressMessage(queueItem, ExtractFileStep.WAITING, Lang.JAVA, locale);
        }
    }

    @Override
    public InlineKeyboardMarkup getWaitingKeyboard(UnzipQueueItem queueItem, Locale locale) {
        return inlineKeyboardService.getUnzipProcessingKeyboard(queueItem.getId(), locale);
    }
}
