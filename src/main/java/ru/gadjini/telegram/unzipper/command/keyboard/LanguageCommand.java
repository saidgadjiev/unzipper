package ru.gadjini.telegram.unzipper.command.keyboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.command.api.BotCommand;
import ru.gadjini.telegram.smart.bot.commons.command.api.KeyboardBotCommand;
import ru.gadjini.telegram.smart.bot.commons.command.api.NavigableBotCommand;
import ru.gadjini.telegram.unzipper.common.CommandNames;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.HtmlMessage;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Message;
import ru.gadjini.telegram.smart.bot.commons.service.command.navigator.CommandNavigator;
import ru.gadjini.telegram.unzipper.service.keyboard.UnzipBotReplyKeyboardService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class LanguageCommand implements KeyboardBotCommand, NavigableBotCommand, BotCommand {

    private Set<String> names = new HashSet<>();

    private final LocalisationService localisationService;

    private MessageService messageService;

    private UserService userService;

    private UnzipBotReplyKeyboardService replyKeyboardService;

    private CommandNavigator commandNavigator;

    @Autowired
    public LanguageCommand(LocalisationService localisationService, @Qualifier("messageLimits") MessageService messageService,
                           UserService userService, @Qualifier("curr") UnzipBotReplyKeyboardService replyKeyboardService) {
        this.localisationService = localisationService;
        this.messageService = messageService;
        this.userService = userService;
        this.replyKeyboardService = replyKeyboardService;
        for (Locale locale : localisationService.getSupportedLocales()) {
            this.names.add(localisationService.getMessage(MessagesProperties.LANGUAGE_COMMAND_NAME, locale));
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
    public void processMessage(Message message, String[] params) {
        processMessage(message, (String) null);
    }

    @Override
    public String getCommandIdentifier() {
        return CommandNames.LANGUAGE_COMMAND_NAME;
    }

    @Override
    public boolean processMessage(Message message, String text) {
        processMessage0(message.getChatId(), message.getFrom().getId());

        return true;
    }

    private void processMessage0(long chatId, int userId) {
        Locale locale = userService.getLocaleOrDefault(userId);
        messageService.sendMessage(new HtmlMessage(chatId, localisationService.getMessage(MessagesProperties.MESSAGE_CHOOSE_LANGUAGE, locale))
                .setReplyMarkup(replyKeyboardService.languageKeyboard(chatId, locale)));
    }

    @Override
    public String getParentCommandName(long chatId) {
        return CommandNames.START_COMMAND_NAME;
    }

    @Override
    public String getHistoryName() {
        return CommandNames.LANGUAGE_COMMAND_NAME;
    }

    @Override
    public void processNonCommandUpdate(Message message, String text) {
        text = text.toLowerCase();
        for (Locale locale : localisationService.getSupportedLocales()) {
            if (text.equals(locale.getDisplayLanguage(locale).toLowerCase())) {
                changeLocale(message, locale);
                return;
            }
        }
    }

    private void changeLocale(Message message, Locale locale) {
        userService.changeLocale(message.getFrom().getId(), locale);
        messageService.sendMessage(
                new HtmlMessage(message.getChatId(), localisationService.getMessage(MessagesProperties.MESSAGE_LANGUAGE_SELECTED, locale))
                        .setReplyMarkup(replyKeyboardService.removeKeyboard(message.getChatId()))
        );
        commandNavigator.silentPop(message.getChatId());
    }
}
