package ru.gadjini.telegram.unzipper.command.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.command.api.CallbackBotCommand;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.CallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.job.UnzipperJob;
import ru.gadjini.telegram.unzipper.request.Arg;

@Component
public class CancelExtractFileQueryCommand implements CallbackBotCommand {

    private UnzipperJob unzipperJob;

    @Autowired
    public CancelExtractFileQueryCommand(UnzipperJob unzipperJob) {
        this.unzipperJob = unzipperJob;
    }

    @Override
    public String getName() {
        return UnzipCommandNames.CANCEL_EXTRACT_FILE;
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {
        int jobId = requestParams.getInt(Arg.JOB_ID.getKey());
        unzipperJob.cancelExtractFile(callbackQuery.getMessage().getChatId(), callbackQuery.getMessage().getMessageId(), callbackQuery.getId(), jobId);
    }
}
