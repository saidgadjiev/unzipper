package ru.gadjini.telegram.unzipper.service.keyboard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.dao.command.keyboard.ReplyKeyboardDao;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.ReplyKeyboard;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.ReplyKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.ReplyKeyboardRemove;

import java.util.Locale;

@Service
@Qualifier("curr")
public class CurrReplyKeyboard implements UnzipBotReplyKeyboardService {

    private ReplyKeyboardDao replyKeyboardDao;

    private UnzipBotReplyKeyboardService keyboardService;

    public CurrReplyKeyboard(@Qualifier("inMemory") ReplyKeyboardDao replyKeyboardDao, @Qualifier("keyboard") UnzipBotReplyKeyboardService keyboardService) {
        this.replyKeyboardDao = replyKeyboardDao;
        this.keyboardService = keyboardService;
    }

    @Override
    public ReplyKeyboardMarkup languageKeyboard(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, (ReplyKeyboardMarkup) keyboardService.languageKeyboard(chatId, locale));
    }

    @Override
    public ReplyKeyboardRemove removeKeyboard(long chatId) {
        ReplyKeyboardRemove replyKeyboardRemove = keyboardService.removeKeyboard(chatId);
        setCurrentKeyboard(chatId, new ReplyKeyboardMarkup());

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
