package ru.gadjini.telegram.unzipper.job;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.updatemessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.event.CurrentTasksCanceled;
import ru.gadjini.telegram.smart.bot.commons.service.queue.event.TaskCanceled;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipMessageBuilder;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.io.File;
import java.util.Locale;

@Component
public class QueueJobEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueJobEventListener.class);

    private CommandStateService commandStateService;

    private UserService userService;

    private UnzipMessageBuilder messageBuilder;

    private InlineKeyboardService inlineKeyboardService;

    private MessageService messageService;

    @Autowired
    public QueueJobEventListener(CommandStateService commandStateService, UserService userService,
                                 UnzipMessageBuilder messageBuilder, InlineKeyboardService inlineKeyboardService,
                                 @Qualifier("messageLimits") MessageService messageService) {
        this.commandStateService = commandStateService;
        this.userService = userService;
        this.messageBuilder = messageBuilder;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageService = messageService;
    }

    @EventListener
    public void currentTasksCanceled(TaskCanceled event) {
        UnzipQueueItem queueItem = (UnzipQueueItem) event.getQueueItem();
        if (queueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            commandStateService.deleteState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME);

            UnzipState unzipState = commandStateService.getState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null && StringUtils.isNotBlank(unzipState.getArchivePath())) {
                new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
            }
        } else {
            UnzipState unzipState = commandStateService.getState(queueItem.getUserId(), UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
            if (unzipState != null) {
                Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
                UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, unzipState.getOffset(), locale);
                InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), unzipState.getPrevLimit(), filesList.getOffset(), unzipState.getUnzipJobId(), locale);
                messageService.editMessage(new EditMessageText(queueItem.getUserId(), queueItem.getProgressMessageId(), filesList.getMessage())
                        .setReplyMarkup(filesListKeyboard));
            }
        }
    }

    @EventListener
    public void currentTasksCanceled(CurrentTasksCanceled event) {
        int userId = event.getUserId();
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);

        if (unzipState != null) {
            LOGGER.debug("Remove previous state({})", userId);
            if (StringUtils.isNotBlank(unzipState.getArchivePath())) {
                new SmartTempFile(new File(unzipState.getArchivePath())).smartDelete();
            }
            commandStateService.deleteState(userId, UnzipCommandNames.START_COMMAND_NAME);
        }
    }
}
