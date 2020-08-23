package ru.gadjini.any2any.bot.command.keyboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.gadjini.any2any.bot.command.api.BotCommand;
import ru.gadjini.any2any.common.CommandNames;
import ru.gadjini.any2any.common.MessagesProperties;
import ru.gadjini.any2any.model.bot.api.method.send.HtmlMessage;
import ru.gadjini.any2any.model.bot.api.object.Message;
import ru.gadjini.any2any.service.CommandMessageBuilder;
import ru.gadjini.any2any.service.LocalisationService;
import ru.gadjini.any2any.service.UserService;
import ru.gadjini.any2any.service.message.MessageService;

import java.util.Locale;

@Component
public class HelpCommand implements BotCommand {

    private final MessageService messageService;

    private LocalisationService localisationService;

    private UserService userService;

    private CommandMessageBuilder commandMessageBuilder;

    @Autowired
    public HelpCommand(@Qualifier("messagelimits") MessageService messageService, LocalisationService localisationService,
                       UserService userService, CommandMessageBuilder commandMessageBuilder) {
        this.messageService = messageService;
        this.localisationService = localisationService;
        this.userService = userService;
        this.commandMessageBuilder = commandMessageBuilder;
    }

    @Override
    public void processMessage(Message message, String[] params) {
        sendHelpMessage(message.getFrom().getId(), userService.getLocaleOrDefault(message.getFrom().getId()));
    }

    @Override
    public String getCommandIdentifier() {
        return CommandNames.HELP_COMMAND;
    }

    private void sendHelpMessage(int userId, Locale locale) {
        messageService.sendMessage(
                new HtmlMessage((long) userId, localisationService.getMessage(MessagesProperties.MESSAGE_HELP,
                        new Object[]{commandMessageBuilder.getCommandsInfo(locale)},
                        locale)));
    }
}
