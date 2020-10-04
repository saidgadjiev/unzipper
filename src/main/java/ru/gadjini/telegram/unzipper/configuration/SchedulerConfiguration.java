package ru.gadjini.telegram.unzipper.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.unzipper.job.UnzipperJob;

import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class SchedulerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerConfiguration.class);

    private UnzipperJob unzipperJob;

    @Autowired
    public void setUserService(UnzipperJob unzipperJob) {
        this.unzipperJob = unzipperJob;
    }

    @Bean
    @Qualifier("unzipTaskExecutor")
    public SmartExecutorService unzipTaskExecutor(UserService userService, FileManager fileManager,
                                                  @Qualifier("messageLimits") MessageService messageService, LocalisationService localisationService) {
        ThreadPoolExecutor lightTaskExecutor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
        ThreadPoolExecutor heavyTaskExecutor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new SynchronousQueue<>());

        LOGGER.debug("Unzip light thread pool({})", lightTaskExecutor.getCorePoolSize());
        LOGGER.debug("Unzip heavy thread pool({})", heavyTaskExecutor.getCorePoolSize());

        SmartExecutorService executorService = new SmartExecutorService(messageService, localisationService, fileManager, userService);
        executorService.setExecutors(Map.of(SmartExecutorService.JobWeight.LIGHT, lightTaskExecutor, SmartExecutorService.JobWeight.HEAVY, heavyTaskExecutor));

        executorService.setRejectJobHandler(SmartExecutorService.JobWeight.LIGHT, (job) -> unzipperJob.rejectTask(job));
        executorService.setRejectJobHandler(SmartExecutorService.JobWeight.HEAVY, (job) -> unzipperJob.rejectTask(job));

        return executorService;
    }
}
