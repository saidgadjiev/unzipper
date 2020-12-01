package ru.gadjini.telegram.unzipper.job;

import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;

public class Extra {

    private UnzipQueueItem.ItemType itemType;

    private int cacheFileKey;

    private int progressMessageId;

    private int lastFileIndex;

    private int queuePosition;

    public Extra() {
    }

    public Extra(UnzipQueueItem.ItemType itemType, int cacheFileKey, int progressMessageId, int lastFileIndex, int queuePosition) {
        this.itemType = itemType;
        this.cacheFileKey = cacheFileKey;
        this.progressMessageId = progressMessageId;
        this.lastFileIndex = lastFileIndex;
        this.queuePosition = queuePosition;
    }

    public int getCacheFileKey() {
        return cacheFileKey;
    }

    public UnzipQueueItem.ItemType getItemType() {
        return itemType;
    }

    public int getProgressMessageId() {
        return progressMessageId;
    }

    public int getLastFileIndex() {
        return lastFileIndex;
    }

    public int getQueuePosition() {
        return queuePosition;
    }
}
