package ru.gadjini.telegram.unzipper.service.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.model.MessageMedia;
import ru.gadjini.telegram.unzipper.dao.UnzipQueueDao;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;

@Service
public class UnzipQueueService {

    private UnzipQueueDao unzipQueueDao;

    @Autowired
    public UnzipQueueService(UnzipQueueDao unzipQueueDao) {
        this.unzipQueueDao = unzipQueueDao;
    }

    public UnzipQueueItem createUnzipItem(int userId, MessageMedia any2AnyFile) {
        UnzipQueueItem queueItem = new UnzipQueueItem();
        queueItem.setUserId(userId);
        queueItem.setType(any2AnyFile.getFormat());
        queueItem.setItemType(UnzipQueueItem.ItemType.UNZIP);

        queueItem.setFile(any2AnyFile.toTgFile());

        queueItem.setStatus(UnzipQueueItem.Status.WAITING);

        int id = unzipQueueDao.create(queueItem);
        queueItem.setId(id);

        return queueItem;
    }

    public UnzipQueueItem createExtractFileItem(int userId, int messageId, int extractFileId, long extractFileSize) {
        UnzipQueueItem item = new UnzipQueueItem();
        item.setUserId(userId);
        item.setExtractFileId(extractFileId);
        item.setMessageId(messageId);
        item.setItemType(UnzipQueueItem.ItemType.EXTRACT_FILE);
        item.setStatus(UnzipQueueItem.Status.WAITING);
        item.setExtractFileSize(extractFileSize);

        int jobId = unzipQueueDao.create(item);
        item.setId(jobId);

        return item;
    }

    public UnzipQueueItem createExtractAllItem(int userId, int messageId, long size) {
        UnzipQueueItem item = new UnzipQueueItem();
        item.setUserId(userId);
        item.setMessageId(messageId);
        item.setExtractFileSize(size);
        item.setStatus(UnzipQueueItem.Status.WAITING);
        item.setItemType(UnzipQueueItem.ItemType.EXTRACT_ALL);

        int jobId = unzipQueueDao.create(item);
        item.setId(jobId);

        return item;
    }
}
