package ru.gadjini.any2any.bot.command.keyboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.any2any.bot.command.api.KeyboardBotCommand;
import ru.gadjini.any2any.common.MessagesProperties;
import ru.gadjini.any2any.model.TgMessage;
import ru.gadjini.any2any.model.bot.api.object.Message;
import ru.gadjini.any2any.service.LocalisationService;
import ru.gadjini.any2any.service.command.navigator.CommandNavigator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class CancelKeyboardCommand implements KeyboardBotCommand {

    private CommandNavigator commandNavigator;

    private Set<String> names = new HashSet<>();

    @Autowired
    public CancelKeyboardCommand(LocalisationService localisationService) {
        for (Locale locale : localisationService.getSupportedLocales()) {
            this.names.add(localisationService.getMessage(MessagesProperties.CANCEL_COMMAND_DESCRIPTION, locale));
        }
    }

    @Autowired
    public void setCommandNavigator(CommandNavigator commandNavigator) {
        this.commandNavigator = commandNavigator;
    }

    @Override
    public boolean canHandle(long chatId, String command) {
        return names.contains(command);
    }

    @Override
    public boolean processMessage(Message message, String text) {
        commandNavigator.pop(TgMessage.from(message));

        return false;
    }
}
