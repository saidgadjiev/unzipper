package ru.gadjini.telegram.unzipper.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.gadjini.telegram.smart.bot.commons.filter.*;
import ru.gadjini.telegram.unzipper.filter.Any2AnyBotFilter;

@Configuration
public class BotConfiguration {

    @Bean
    public BotFilter botFilter(Any2AnyBotFilter any2AnyBotFilter,
                               UpdateFilter updateFilter, StartCommandFilter startCommandFilter,
                               MediaFilter mediaFilter, LastActivityFilter activityFilter,
                               SubscriptionFilter subscriptionFilter) {
        updateFilter.setNext(mediaFilter).setNext(startCommandFilter).setNext(subscriptionFilter)
                .setNext(activityFilter).setNext(any2AnyBotFilter);
        return updateFilter;
    }

}
