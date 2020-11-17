package ru.gadjini.telegram.unzipper.command.callback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.command.api.CallbackBotCommand;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.request.Arg;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipService;

@Component
public class ExtractFileCommand implements CallbackBotCommand {

    private UnzipService unzipService;

    @Autowired
    public ExtractFileCommand(UnzipService unzipService) {
        this.unzipService = unzipService;
    }

    @Override
    public String getName() {
        return UnzipCommandNames.EXTRACT_FILE_COMMAND_NAME;
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {
        int id = requestParams.getInt(Arg.EXTRACT_FILE_ID.getKey());
        int unzipJobId = requestParams.getInt(Arg.JOB_ID.getKey());

        unzipService.extractFile(callbackQuery.getFrom().getId(), callbackQuery.getMessage().getMessageId(), unzipJobId, id, callbackQuery.getId());
    }
}
