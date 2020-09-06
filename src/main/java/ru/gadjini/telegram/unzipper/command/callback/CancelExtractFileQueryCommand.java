package ru.gadjini.telegram.unzipper.command.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.command.api.CallbackBotCommand;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.CallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.common.CommandNames;
import ru.gadjini.telegram.unzipper.request.Arg;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipService;

@Component
public class CancelExtractFileQueryCommand implements CallbackBotCommand {

    private UnzipService unzipService;

    @Autowired
    public CancelExtractFileQueryCommand(UnzipService unzipService) {
        this.unzipService = unzipService;
    }

    @Override
    public String getName() {
        return CommandNames.CANCEL_EXTRACT_FILE;
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {
        int jobId = requestParams.getInt(Arg.JOB_ID.getKey());
        unzipService.cancelExtractFile(callbackQuery.getMessage().getChatId(), callbackQuery.getMessage().getMessageId(), callbackQuery.getId(), jobId);
    }
}
