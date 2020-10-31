package ru.gadjini.telegram.unzipper.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.unzipper.job.UnzipperJobDelegate;

@Component
public class ContextCloseListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextCloseListener.class);

    private UnzipperJobDelegate unzipperJob;

    private FileManager fileManager;

    public ContextCloseListener(UnzipperJobDelegate unzipperJob, FileManager fileManager) {
        this.unzipperJob = unzipperJob;
        this.fileManager = fileManager;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            unzipperJob.shutdown();
        } catch (Throwable e) {
            LOGGER.error("Error shutdown unzipService. " + e.getMessage(), e);
        }

        try {
            fileManager.cancelDownloads();
        } catch (Throwable e) {
            LOGGER.error("Error cancel downloading telegramService. " + e.getMessage(), e);
        }
        LOGGER.debug("Shutdown success");
    }
}
