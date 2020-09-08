package ru.gadjini.telegram.unzipper.command.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.command.api.CallbackBotCommand;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.CallbackQuery;
import ru.gadjini.telegram.unzipper.request.Arg;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipService;

@Component
public class ExtractAllCommand implements CallbackBotCommand {

    private UnzipService unzipService;

    @Autowired
    public ExtractAllCommand(UnzipService unzipService) {
        this.unzipService = unzipService;
    }

    @Override
    public String getName() {
        return UnzipCommandNames.EXTRACT_ALL;
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {
        int unzipJobId = requestParams.getInt(Arg.JOB_ID.getKey());

        unzipService.extractAll(callbackQuery.getFrom().getId(), callbackQuery.getMessage().getMessageId(), unzipJobId, callbackQuery.getId());
    }
}
