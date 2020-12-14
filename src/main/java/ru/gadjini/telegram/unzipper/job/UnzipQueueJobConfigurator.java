package ru.gadjini.telegram.unzipper.job;

import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueJobConfigurator;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;

@Component
public class UnzipQueueJobConfigurator implements QueueJobConfigurator<UnzipQueueItem> {

    @Override
    public boolean isNeedUpdateMessageAfterCancel(UnzipQueueItem queueItem) {
        return queueItem == null || queueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP;
    }

}
