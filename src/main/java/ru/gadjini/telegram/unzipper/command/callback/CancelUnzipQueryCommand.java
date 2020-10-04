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
public class CancelUnzipQueryCommand implements CallbackBotCommand {

    private UnzipperJob unzipperJob;

    @Autowired
    public CancelUnzipQueryCommand(UnzipperJob unzipperJob) {
        this.unzipperJob = unzipperJob;
    }

    @Override
    public String getName() {
        return UnzipCommandNames.CANCEL_UNZIP_QUERY;
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {
        int jobId = requestParams.getInt(Arg.JOB_ID.getKey());
        unzipperJob.cancelUnzip(callbackQuery.getMessage().getChatId(), callbackQuery.getMessage().getMessageId(), callbackQuery.getId(), jobId);
    }
}
