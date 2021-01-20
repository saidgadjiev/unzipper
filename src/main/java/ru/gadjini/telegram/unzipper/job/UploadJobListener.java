package ru.gadjini.telegram.unzipper.job;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.common.CommandNames;
import ru.gadjini.telegram.smart.bot.commons.domain.UploadQueueItem;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.event.UploadCompleted;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.ListIterator;

@Component
public class UploadJobListener {

    private CommandStateService commandStateService;

    private Gson gson;

    private ArchiveExtractor extractProcessorFactory;

    @Autowired
    public UploadJobListener(CommandStateService commandStateService, Gson gson, ArchiveExtractor extractProcessorFactory) {
        this.commandStateService = commandStateService;
        this.gson = gson;
        this.extractProcessorFactory = extractProcessorFactory;
    }

    @EventListener
    public void uploadCompleted(UploadCompleted uploadCompleted) {
        SendFileResult result = uploadCompleted.getSendFileResult();
        UploadQueueItem item = uploadCompleted.getUploadQueueItem();

        UnzipState unzipState = commandStateService.getState(item.getUserId(), CommandNames.START_COMMAND_NAME, true, UnzipState.class);
        Extra extra = gson.fromJson((JsonElement) item.getExtra(), Extra.class);

        uploadCompleted(item, unzipState, extra, result);
        extractUploadCompleted(item, extra, unzipState);
    }

    private void uploadCompleted(UploadQueueItem item, UnzipState unzipState, Extra extra, SendFileResult result) {
        if (result != null) {
            unzipState.getFilesCache().put(extra.getCacheFileKey(), result.getFileId());
            commandStateService.setState(item.getUserId(), CommandNames.START_COMMAND_NAME, unzipState);
        }
    }

    private void extractUploadCompleted(UploadQueueItem item, Extra extra, UnzipState unzipState) {
        if (extra.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            extractFileUploadCompleted(item, extra, unzipState);
        } else if (extra.getItemType() == UnzipQueueItem.ItemType.EXTRACT_ALL) {
            extractAllUploadCompleted(item, extra, unzipState);
        }
    }

    private void extractAllUploadCompleted(UploadQueueItem item, Extra extra, UnzipState unzipState) {
        ListIterator<ArchiveExtractor.ArchiveFile> iterator = extractProcessorFactory.getIterator(extra, unzipState.getFiles());

        boolean allUploaded = true;
        while (iterator.hasNext()) {
            ArchiveExtractor.ArchiveFile archiveFile = iterator.next();
            ArchiveExtractor.ExtractedFile extract = archiveFile.extract(item.getUserId(), unzipState);
            extractProcessorFactory.sendExtractedFile(item, extra, extract);

            if (extract.isNewMedia()) {
                allUploaded = false;
                break;
            }
        }
        if (allUploaded) {
            extractProcessorFactory.finishExtracting(item.getUserId(), extra.getProgressMessageId(), unzipState);
        }
    }

    private void extractFileUploadCompleted(UploadQueueItem item, Extra extra, UnzipState unzipState) {
        extractProcessorFactory.finishExtracting(item.getUserId(), extra.getProgressMessageId(), unzipState);
    }
}
