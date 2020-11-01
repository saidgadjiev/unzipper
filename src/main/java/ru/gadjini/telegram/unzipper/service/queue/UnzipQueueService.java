package ru.gadjini.telegram.unzipper.service.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.model.MessageMedia;
import ru.gadjini.telegram.smart.bot.commons.property.FileLimitProperties;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.unzipper.dao.UnzipQueueDao;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;

@Service
public class UnzipQueueService {

    private UnzipQueueDao unzipQueueDao;

    private FileLimitProperties fileLimitProperties;

    @Autowired
    public UnzipQueueService(UnzipQueueDao unzipQueueDao, FileLimitProperties fileLimitProperties) {
        this.unzipQueueDao = unzipQueueDao;
        this.fileLimitProperties = fileLimitProperties;
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
        queueItem.setQueuePosition(
                unzipQueueDao.getQueuePosition(id, queueItem.getFile().getSize() > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT)
        );

        return queueItem;
    }

    public UnzipQueueItem createExtractFileItem(int userId, int messageId, int extractFileId, long extractFileSize) {
        UnzipQueueItem item = new UnzipQueueItem();
        item.setUserId(userId);
        item.setExtractFileId(extractFileId);
        item.setProgressMessageId(messageId);
        item.setItemType(UnzipQueueItem.ItemType.EXTRACT_FILE);
        item.setStatus(UnzipQueueItem.Status.WAITING);
        item.setExtractFileSize(extractFileSize);

        int jobId = unzipQueueDao.create(item);
        item.setId(jobId);
        item.setQueuePosition(
                unzipQueueDao.getQueuePosition(jobId, extractFileSize > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT)
        );

        return item;
    }

    public UnzipQueueItem createExtractAllItem(int userId, int messageId, long size) {
        UnzipQueueItem item = new UnzipQueueItem();
        item.setUserId(userId);
        item.setProgressMessageId(messageId);
        item.setExtractFileSize(size);
        item.setStatus(UnzipQueueItem.Status.WAITING);
        item.setItemType(UnzipQueueItem.ItemType.EXTRACT_ALL);

        int jobId = unzipQueueDao.create(item);
        item.setId(jobId);

        item.setQueuePosition(
                unzipQueueDao.getQueuePosition(jobId, size > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT)
        );

        return item;
    }
}
