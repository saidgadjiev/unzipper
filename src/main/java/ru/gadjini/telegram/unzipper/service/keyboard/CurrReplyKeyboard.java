package ru.gadjini.telegram.unzipper.service.keyboard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import ru.gadjini.telegram.smart.bot.commons.dao.command.keyboard.ReplyKeyboardDao;
import ru.gadjini.telegram.smart.bot.commons.service.keyboard.ReplyKeyboardService;

import java.util.Locale;

@Service
@Qualifier("curr")
public class CurrReplyKeyboard implements ReplyKeyboardService {

    private ReplyKeyboardDao replyKeyboardDao;

    private ReplyKeyboardService keyboardService;

    public CurrReplyKeyboard(@Qualifier("inMemory") ReplyKeyboardDao replyKeyboardDao, @Qualifier("keyboard") ReplyKeyboardService keyboardService) {
        this.replyKeyboardDao = replyKeyboardDao;
        this.keyboardService = keyboardService;
    }

    @Override
    public ReplyKeyboardMarkup languageKeyboard(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.languageKeyboard(chatId, locale));
    }

    @Override
    public ReplyKeyboardRemove removeKeyboard(long chatId) {
        ReplyKeyboardRemove replyKeyboardRemove = keyboardService.removeKeyboard(chatId);
        setCurrentKeyboard(chatId, replyKeyboardMarkup());

        return replyKeyboardRemove;
    }

    @Override
    public ReplyKeyboard getMainMenu(long chatId, Locale locale) {
        return removeKeyboard(chatId);
    }

    public ReplyKeyboardMarkup getCurrentReplyKeyboard(long chatId) {
        return replyKeyboardDao.get(chatId);
    }

    private ReplyKeyboardMarkup setCurrentKeyboard(long chatId, ReplyKeyboardMarkup replyKeyboardMarkup) {
        replyKeyboardDao.store(chatId, replyKeyboardMarkup);

        return replyKeyboardMarkup;
    }
}
