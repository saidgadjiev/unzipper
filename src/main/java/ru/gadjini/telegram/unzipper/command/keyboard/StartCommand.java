package ru.gadjini.telegram.unzipper.command.keyboard;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import ru.gadjini.telegram.smart.bot.commons.command.api.BotCommand;
import ru.gadjini.telegram.smart.bot.commons.command.api.CallbackBotCommand;
import ru.gadjini.telegram.smart.bot.commons.command.api.NavigableBotCommand;
import ru.gadjini.telegram.smart.bot.commons.common.CommandNames;
import ru.gadjini.telegram.smart.bot.commons.exception.UserException;
import ru.gadjini.telegram.smart.bot.commons.job.WorkQueueJob;
import ru.gadjini.telegram.smart.bot.commons.model.MessageMedia;
import ru.gadjini.telegram.smart.bot.commons.model.TgMessage;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.MessageMediaService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.format.FormatCategory;
import ru.gadjini.telegram.smart.bot.commons.service.format.FormatService;
import ru.gadjini.telegram.smart.bot.commons.service.keyboard.ReplyKeyboardService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.job.UnzipQueueWorkerFactory;
import ru.gadjini.telegram.unzipper.request.Arg;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipService;
import ru.gadjini.telegram.unzipper.service.unzip.UnzipState;

import java.util.Locale;

@Component
public class StartCommand implements NavigableBotCommand, BotCommand, CallbackBotCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartCommand.class);

    private UnzipService unzipService;

    private WorkQueueJob queueJob;

    private LocalisationService localisationService;

    private MessageService messageService;

    private ReplyKeyboardService replyKeyboardService;

    private UserService userService;

    private FormatService formatService;

    private MessageMediaService fileService;

    private CommandStateService commandStateService;

    @Autowired
    public StartCommand(LocalisationService localisationService, UnzipService unzipService,
                        WorkQueueJob queueJob, @Qualifier("messageLimits") MessageService messageService,
                        @Qualifier("curr") ReplyKeyboardService replyKeyboardService,
                        UserService userService, FormatService formatService, MessageMediaService fileService,
                        CommandStateService commandStateService) {
        this.localisationService = localisationService;
        this.unzipService = unzipService;
        this.queueJob = queueJob;
        this.messageService = messageService;
        this.replyKeyboardService = replyKeyboardService;
        this.userService = userService;
        this.formatService = formatService;
        this.fileService = fileService;
        this.commandStateService = commandStateService;
    }

    @Override
    public boolean acceptNonCommandMessage(Message message) {
        return message.hasDocument() || message.hasText();
    }

    @Override
    public String getParentCommandName(long chatId) {
        return CommandNames.START_COMMAND_NAME;
    }

    @Override
    public String getHistoryName() {
        return CommandNames.START_COMMAND_NAME;
    }

    @Override
    public void processMessage(Message message, String[] params) {
        Locale locale = userService.getLocaleOrDefault(message.getFrom().getId());
        messageService.sendMessage(SendMessage.builder().chatId(String.valueOf(message.getChatId()))
                .text(localisationService.getMessage(MessagesProperties.MESSAGE_ZIP_FILE, locale))
                .replyMarkup(replyKeyboardService.removeKeyboard(message.getChatId()))
                .parseMode(ParseMode.HTML).build());
    }

    @Override
    public String getCommandIdentifier() {
        return CommandNames.START_COMMAND_NAME;
    }

    @Override
    public void processNonCommandUpdate(Message message, String text) {
        Locale locale = userService.getLocaleOrDefault(message.getFrom().getId());
        if (message.hasDocument()) {
            Format format = formatService.getFormat(message.getDocument().getFileName(), message.getDocument().getMimeType());
            MessageMedia file = fileService.getMedia(message, locale);
            file.setFormat(checkFormat(message.getFrom().getId(), format, message.getDocument().getMimeType(), message.getDocument().getFileName(), locale));

            queueJob.removeAndCancelCurrentTasks(message.getChatId());
            UnzipState unzipState = createState(message, file.getFormat());
            commandStateService.setState(message.getChatId(), CommandNames.START_COMMAND_NAME, unzipState);
            unzipService.unzip(message.getFrom().getId(), file, locale);
        } else {
            UnzipState state = commandStateService.getState(message.getChatId(), CommandNames.START_COMMAND_NAME, false, UnzipState.class);

            if (state == null) {
                messageService.sendMessage(
                        SendMessage.builder().chatId(String.valueOf(message.getChatId()))
                                .text(localisationService.getMessage(MessagesProperties.MESSAGE_UNZIP_NOT_FOUND, locale)).build()
                );
            } else {
                state.setPassword(message.getText());
                commandStateService.setState(message.getChatId(), CommandNames.START_COMMAND_NAME, state);

                messageService.sendMessage(
                        SendMessage.builder().chatId(String.valueOf(message.getChatId()))
                                .text(localisationService.getMessage(MessagesProperties.MESSAGE_PASSWORD_SET, new Object[]{message.getText()}, locale)).build()
                );
            }
        }
    }

    @Override
    public void processNonCommandCallback(CallbackQuery callbackQuery, RequestParams requestParams) {
        if (requestParams.contains(Arg.PAGINATION.getKey())) {
            unzipService.nextOrPrev(callbackQuery.getMessage().getChatId(), callbackQuery.getFrom().getId(),
                    callbackQuery.getMessage().getMessageId(), requestParams.getInt(Arg.PREV_LIMIT.getKey()), requestParams.getInt(Arg.OFFSET.getKey()));
        }
    }

    @Override
    public String getName() {
        return getHistoryName();
    }

    @Override
    public void processMessage(CallbackQuery callbackQuery, RequestParams requestParams) {

    }

    @Override
    public ReplyKeyboard getKeyboard(long chatId) {
        return replyKeyboardService.removeKeyboard(chatId);
    }

    @Override
    public void restore(TgMessage message) {
        commandStateService.deleteState(message.getChatId(), getHistoryName());
        Locale locale = userService.getLocaleOrDefault(message.getUser().getId());
        messageService.sendMessage(SendMessage.builder().chatId(String.valueOf(message.getChatId()))
                .text(localisationService.getMessage(MessagesProperties.MESSAGE_MAIN_MENU, locale))
                .replyMarkup(replyKeyboardService.getMainMenu(message.getChatId(), locale))
                .parseMode(ParseMode.HTML).build());
    }

    @Override
    public String getMessage(long chatId) {
        Locale locale = userService.getLocaleOrDefault((int) chatId);
        return localisationService.getMessage(MessagesProperties.MESSAGE_MAIN_MENU, locale);
    }

    private Format checkFormat(int userId, Format format, String mimeType, String fileName, Locale locale) {
        if (format == null) {
            LOGGER.warn("Format is null({}, {}, {})", userId, mimeType, fileName);
            throw new UserException(localisationService.getMessage(MessagesProperties.MESSAGE_SUPPORTED_ZIP_FORMATS, locale));
        }
        if (format.getCategory() != FormatCategory.ARCHIVE) {
            LOGGER.warn("No archive({}, {})", userId, format.getCategory());
            throw new UserException(localisationService.getMessage(MessagesProperties.MESSAGE_SUPPORTED_ZIP_FORMATS, locale));
        }

        return format;
    }

    private UnzipState createState(Message message, Format archiveType) {
        UnzipState unzipState = new UnzipState();
        unzipState.setArchiveType(archiveType);
        unzipState.setPassword(StringUtils.isNotBlank(message.getCaption()) ? message.getCaption() : UnzipQueueWorkerFactory.DEFAULT_PASSWORD);

        return unzipState;
    }
}
