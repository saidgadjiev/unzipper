package ru.gadjini.telegram.unzipper.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.domain.UploadQueueItem;
import ru.gadjini.telegram.smart.bot.commons.model.SendFileResult;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.event.UploadCompleted;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

@Component
public class UploadJobListener {

    private CommandStateService commandStateService;

    @Autowired
    public UploadJobListener(CommandStateService commandStateService) {
        this.commandStateService = commandStateService;
    }

    @EventListener
    public void uploadCompleted(UploadCompleted uploadCompleted) {
        SendFileResult result = uploadCompleted.getSendFileResult();
        UploadQueueItem item = uploadCompleted.getUploadQueueItem();
        if (result != null) {
            UnzipState unzipState = commandStateService.getState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null && item.getExtra() != null) {
                unzipState.getFilesCache().put(item.getExtra().getAsInt(), result.getFileId());
                commandStateService.setState(item.getUserId(), UnzipCommandNames.START_COMMAND_NAME, unzipState);
            }
        }
    }
}
