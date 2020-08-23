package ru.gadjini.any2any.service.keyboard;

import ru.gadjini.any2any.model.bot.api.object.replykeyboard.ReplyKeyboardMarkup;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.ReplyKeyboardRemove;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public interface ReplyKeyboardService {

    ReplyKeyboardMarkup getAdminKeyboard(long chatId, Locale locale);

    ReplyKeyboardMarkup archiveTypesKeyboard(long chatId, Locale locale);

    ReplyKeyboardMarkup languageKeyboard(long chatId, Locale locale);

    ReplyKeyboardMarkup getMainMenu(long chatId, Locale locale);

    ReplyKeyboardMarkup goBack(long chatId, Locale locale);

    ReplyKeyboardMarkup cancel(long chatId, Locale locale);

    ReplyKeyboardRemove removeKeyboard(long chatId);

    default KeyboardRow keyboardRow(String... buttons) {
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.addAll(Arrays.asList(buttons));

        return keyboardRow;
    }

    default ReplyKeyboardMarkup replyKeyboardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        replyKeyboardMarkup.setKeyboard(new ArrayList<>());
        replyKeyboardMarkup.setResizeKeyboard(true);

        return replyKeyboardMarkup;
    }
}
