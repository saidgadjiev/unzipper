package ru.gadjini.any2any.service.keyboard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.gadjini.any2any.dao.command.keyboard.ReplyKeyboardDao;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.ReplyKeyboardMarkup;
import ru.gadjini.any2any.model.bot.api.object.replykeyboard.ReplyKeyboardRemove;

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
    public ReplyKeyboardMarkup getAdminKeyboard(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.getAdminKeyboard(chatId, locale));
    }

    @Override
    public ReplyKeyboardMarkup archiveTypesKeyboard(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.archiveTypesKeyboard(chatId, locale));
    }

    @Override
    public ReplyKeyboardMarkup languageKeyboard(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.languageKeyboard(chatId, locale));
    }

    @Override
    public ReplyKeyboardMarkup getMainMenu(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.getMainMenu(chatId, locale));
    }

    @Override
    public ReplyKeyboardMarkup goBack(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.goBack(chatId, locale));
    }

    @Override
    public ReplyKeyboardMarkup cancel(long chatId, Locale locale) {
        return setCurrentKeyboard(chatId, keyboardService.cancel(chatId, locale));
    }

    @Override
    public ReplyKeyboardRemove removeKeyboard(long chatId) {
        ReplyKeyboardRemove replyKeyboardRemove = keyboardService.removeKeyboard(chatId);
        setCurrentKeyboard(chatId, new ReplyKeyboardMarkup());

        return replyKeyboardRemove;
    }

    public ReplyKeyboardMarkup getCurrentReplyKeyboard(long chatId) {
        return replyKeyboardDao.get(chatId);
    }

    private ReplyKeyboardMarkup setCurrentKeyboard(long chatId, ReplyKeyboardMarkup replyKeyboardMarkup) {
        replyKeyboardDao.store(chatId, replyKeyboardMarkup);

        return replyKeyboardMarkup;
    }
}
