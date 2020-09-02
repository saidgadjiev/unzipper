package ru.gadjini.any2any.bot.command.keyboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.gadjini.any2any.bot.command.api.BotCommand;
import ru.gadjini.any2any.bot.command.api.CallbackBotCommand;
import ru.gadjini.any2any.bot.command.api.NavigableBotCommand;
import ru.gadjini.any2any.common.CommandNames;
import ru.gadjini.any2any.common.MessagesProperties;
import ru.gadjini.any2any.exception.UserException;
import ru.gadjini.any2any.model.Any2AnyFile;
import ru.gadjini.any2any.model.bot.api.method.send.HtmlMessage;
import ru.gadjini.any2any.model.bot.api.object.CallbackQuery;
import ru.gadjini.any2any.model.bot.api.object.Message;
import ru.gadjini.any2any.request.Arg;
import ru.gadjini.any2any.request.RequestParams;
import ru.gadjini.any2any.service.FileService;
import ru.gadjini.any2any.service.LocalisationService;
import ru.gadjini.any2any.service.UserService;
import ru.gadjini.any2any.service.command.CommandStateService;
import ru.gadjini.any2any.service.conversion.api.Format;
import ru.gadjini.any2any.service.conversion.api.FormatCategory;
import ru.gadjini.any2any.service.conversion.impl.FormatService;
import ru.gadjini.any2any.service.keyboard.ReplyKeyboardService;
import ru.gadjini.any2any.service.message.MessageService;
import ru.gadjini.any2any.service.unzip.UnzipService;
import ru.gadjini.any2any.service.unzip.UnzipState;

import java.util.Locale;

@Component
public class StartCommand implements NavigableBotCommand, BotCommand, CallbackBotCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartCommand.class);

    private UnzipService unzipService;

    private LocalisationService localisationService;

    private MessageService messageService;

    private ReplyKeyboardService replyKeyboardService;

    private UserService userService;

    private FormatService formatService;

    private FileService fileService;

    private CommandStateService commandStateService;

    @Autowired
    public StartCommand(LocalisationService localisationService, UnzipService unzipService,
                        @Qualifier("messagelimits") MessageService messageService, @Qualifier("curr") ReplyKeyboardService replyKeyboardService,
                        UserService userService, FormatService formatService, FileService fileService, CommandStateService commandStateService) {
        this.localisationService = localisationService;
        this.unzipService = unzipService;
        this.messageService = messageService;
        this.replyKeyboardService = replyKeyboardService;
        this.userService = userService;
        this.formatService = formatService;
        this.fileService = fileService;
        this.commandStateService = commandStateService;
    }

    @Override
    public boolean accept(Message message) {
        return message.hasDocument();
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
        messageService.sendMessage(new HtmlMessage(message.getChatId(), localisationService.getMessage(MessagesProperties.MESSAGE_ZIP_FILE, locale))
                .setReplyMarkup(replyKeyboardService.removeKeyboard(message.getChatId())));
    }

    @Override
    public String getCommandIdentifier() {
        return CommandNames.START_COMMAND_NAME;
    }

    @Override
    public void processNonCommandUpdate(Message message, String text) {
        Format format = formatService.getFormat(message.getDocument().getFileName(), message.getDocument().getMimeType());
        Locale locale = userService.getLocaleOrDefault(message.getFrom().getId());
        Any2AnyFile file = fileService.getFile(message, locale);
        file.setFormat(checkFormat(message.getFrom().getId(), format, message.getDocument().getMimeType(), message.getDocument().getFileName(), locale));

        unzipService.removeAndCancelCurrentTasks(message.getChatId());
        UnzipState unzipState = createState(file.getFormat());
        commandStateService.setState(message.getChatId(), CommandNames.START_COMMAND_NAME, unzipState);
        unzipService.unzip(message.getFrom().getId(), message.getMessageId(), file, locale);
    }

    @Override
    public void processNonCommandCallback(CallbackQuery callbackQuery, RequestParams requestParams) {
        if (requestParams.contains(Arg.PAGINATION.getKey())) {
            unzipService.nextOrPrev(callbackQuery.getId(), callbackQuery.getMessage().getChatId(), callbackQuery.getFrom().getId(),
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

    private UnzipState createState(Format archiveType) {
        UnzipState unzipState = new UnzipState();
        unzipState.setArchiveType(archiveType);

        return unzipState;
    }
}
