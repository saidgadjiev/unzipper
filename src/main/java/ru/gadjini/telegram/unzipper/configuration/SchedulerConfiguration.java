package ru.gadjini.telegram.unzipper.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import ru.gadjini.telegram.smart.bot.commons.exception.botapi.TelegramApiRequestException;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipService;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class SchedulerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerConfiguration.class);

    private UnzipService unzipService;

    private UserService userService;

    @Autowired
    public void setUnzipService(UnzipService unzipService) {
        this.unzipService = unzipService;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public TaskScheduler jobsThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(Runtime.getRuntime().availableProcessors());
        threadPoolTaskScheduler.setThreadNamePrefix("JobsThreadPoolTaskScheduler");
        threadPoolTaskScheduler.setErrorHandler(ex -> {
            if (userService.deadlock(ex)) {
                LOGGER.debug("Blocked user({})", ((TelegramApiRequestException) ex).getChatId());
            } else {
                LOGGER.error(ex.getMessage(), ex);
            }
        });

        LOGGER.debug("Jobs thread pool scheduler initialized with pool size({})", threadPoolTaskScheduler.getPoolSize());

        return threadPoolTaskScheduler;
    }

    @Bean
    @Qualifier("unzipTaskExecutor")
    public SmartExecutorService unzipTaskExecutor() {
        SmartExecutorService executorService = new SmartExecutorService();
        ThreadPoolExecutor lightTaskExecutor = new ThreadPoolExecutor(2, 2,
                0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                (r, executor) -> {
                    executorService.complete(((SmartExecutorService.Job) r).getId());
                    unzipService.rejectTask((SmartExecutorService.Job) r);
                }) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                Runnable poll = unzipService.getTask(SmartExecutorService.JobWeight.LIGHT);
                if (poll != null) {
                    execute(poll);
                }
            }
        };
        ThreadPoolExecutor heavyTaskExecutor = new ThreadPoolExecutor(3, 3,
                0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                (r, executor) -> {
                    executorService.complete(((SmartExecutorService.Job) r).getId());
                    unzipService.rejectTask((SmartExecutorService.Job) r);
                }) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                Runnable poll = unzipService.getTask(SmartExecutorService.JobWeight.HEAVY);
                if (poll != null) {
                    execute(poll);
                }
            }
        };

        LOGGER.debug("Unzip light thread pool({})", lightTaskExecutor.getCorePoolSize());
        LOGGER.debug("Unzip heavy thread pool({})", heavyTaskExecutor.getCorePoolSize());

        return executorService.setExecutors(Map.of(SmartExecutorService.JobWeight.LIGHT, lightTaskExecutor, SmartExecutorService.JobWeight.HEAVY, heavyTaskExecutor));
    }
}
