package ru.gadjini.telegram.unzipper.command.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.gadjini.telegram.smart.bot.commons.command.api.BotCommand;
import ru.gadjini.telegram.smart.bot.commons.common.CommandNames;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.Locale;

@Component
public class ClearPasswordCommand implements BotCommand {

    private CommandStateService commandStateService;

    private MessageService messageService;

    private LocalisationService localisationService;

    private UserService userService;

    @Autowired
    public ClearPasswordCommand(CommandStateService commandStateService, @Qualifier("messageLimits") MessageService messageService,
                                LocalisationService localisationService, UserService userService) {
        this.commandStateService = commandStateService;
        this.messageService = messageService;
        this.localisationService = localisationService;
        this.userService = userService;
    }

    @Override
    public void processMessage(Message message, String[] params) {
        UnzipState state = commandStateService.getState(message.getChatId(), CommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (state != null) {
            state.setPassword(null);
            commandStateService.setState(message.getChatId(), CommandNames.START_COMMAND_NAME, state);
        }
        Locale locale = userService.getLocaleOrDefault(message.getFrom().getId());
        messageService.sendMessage(SendMessage.builder().chatId(String.valueOf(message.getChatId()))
                .text(localisationService.getMessage(MessagesProperties.MESSAGE_PASSWORD_CLEARED, locale)).build());
    }

    @Override
    public String getCommandIdentifier() {
        return UnzipCommandNames.CLEAR_PASSWORD;
    }
}
