package ru.gadjini.any2any.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.gadjini.any2any.bot.command.api.BotCommand;
import ru.gadjini.any2any.bot.command.api.NavigableBotCommand;
import ru.gadjini.any2any.common.CommandNames;
import ru.gadjini.any2any.common.MessagesProperties;
import ru.gadjini.any2any.model.bot.api.method.send.HtmlMessage;
import ru.gadjini.any2any.model.bot.api.object.Message;
import ru.gadjini.any2any.model.bot.api.object.Update;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.ReplyKeyboardMarkup;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.buttons.KeyboardRow;
import ru.gadjini.any2any.service.command.CommandExecutor;
import ru.gadjini.any2any.service.command.navigator.CommandNavigator;
import ru.gadjini.any2any.service.keyboard.CurrReplyKeyboard;
import ru.gadjini.any2any.service.message.MessageService;

import java.util.Locale;

@Service
public class Any2AnyBotService {

    private MessageService messageService;

    private CommandExecutor commandExecutor;

    private LocalisationService localisationService;

    private UserService userService;

    private CommandNavigator commandNavigator;

    private CurrReplyKeyboard replyKeyboardService;

    @Autowired
    public Any2AnyBotService(@Qualifier("messagelimits") MessageService messageService, CommandExecutor commandExecutor,
                             LocalisationService localisationService, UserService userService,
                             CommandNavigator commandNavigator, CurrReplyKeyboard replyKeyboardService) {
        this.messageService = messageService;
        this.commandExecutor = commandExecutor;
        this.localisationService = localisationService;
        this.userService = userService;
        this.commandNavigator = commandNavigator;
        this.replyKeyboardService = replyKeyboardService;
    }

    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (restoreCommand(
                    update.getMessage().getChatId(),
                    update.getMessage().hasText() ? update.getMessage().getText().trim() : null
            )) {
                return;
            }
            String text = getText(update.getMessage());
            if (commandExecutor.isKeyboardCommand(update.getMessage().getChatId(), text)) {
                if (isOnCurrentMenu(update.getMessage().getChatId(), text)) {
                    commandExecutor.executeKeyBoardCommand(update.getMessage(), text);

                    return;
                }
            } else if (commandExecutor.isBotCommand(update.getMessage())) {
                if (commandExecutor.executeBotCommand(update.getMessage())) {
                    return;
                } else {
                    messageService.sendMessage(
                            new HtmlMessage(
                                    update.getMessage().getChatId(),
                                    localisationService.getMessage(MessagesProperties.MESSAGE_UNKNOWN_COMMAND, userService.getLocaleOrDefault(update.getMessage().getFrom().getId()))));
                    return;
                }
            }
            commandExecutor.processNonCommandUpdate(update.getMessage(), text);
        } else if (update.hasCallbackQuery()) {
            commandExecutor.executeCallbackCommand(update.getCallbackQuery());
        }
    }

    private String getText(Message message) {
        if (message.hasText()) {
            return message.getText().trim();
        }

        return "";
    }

    private boolean restoreCommand(long chatId, String command) {
        if (StringUtils.isNotBlank(command) && command.startsWith(BotCommand.COMMAND_INIT_CHARACTER + CommandNames.START_COMMAND_NAME)) {
            return false;
        }
        if (commandNavigator.isEmpty(chatId)) {
            commandNavigator.zeroRestore(chatId, (NavigableBotCommand) commandExecutor.getBotCommand(CommandNames.START_COMMAND_NAME));
            Locale locale = userService.getLocaleOrDefault((int) chatId);
            messageService.sendBotRestartedMessage(chatId, replyKeyboardService.removeKeyboard(chatId), locale);

            return true;
        }

        return false;
    }

    private boolean isOnCurrentMenu(long chatId, String commandText) {
        ReplyKeyboardMarkup replyKeyboardMarkup = replyKeyboardService.getCurrentReplyKeyboard(chatId);

        if (replyKeyboardMarkup == null) {
            return true;
        }

        for (KeyboardRow keyboardRow : replyKeyboardMarkup.getKeyboard()) {
            if (keyboardRow.stream().anyMatch(keyboardButton -> keyboardButton.getText().equals(commandText))) {
                return true;
            }
        }

        return false;
    }
}
