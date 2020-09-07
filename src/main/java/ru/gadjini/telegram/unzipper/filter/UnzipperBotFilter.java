package ru.gadjini.telegram.unzipper.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.filter.BaseBotFilter;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Update;
import ru.gadjini.telegram.unzipper.service.UnzipperBotService;

@Component
public class UnzipperBotFilter extends BaseBotFilter {

    private UnzipperBotService any2AnyBotService;

    @Autowired
    public UnzipperBotFilter(UnzipperBotService any2AnyBotService) {
        this.any2AnyBotService = any2AnyBotService;
    }

    @Override
    public void doFilter(Update update) {
        any2AnyBotService.onUpdateReceived(update);
    }
}
