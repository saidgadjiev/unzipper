package ru.gadjini.telegram.unzipper.service.unzip;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.exception.UserException;
import ru.gadjini.telegram.smart.bot.commons.exception.botapi.TelegramApiException;
import ru.gadjini.telegram.smart.bot.commons.model.MessageMedia;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.HtmlMessage;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.send.SendMessage;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.method.updatemessages.EditMessageText;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.AnswerCallbackQuery;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.Message;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.UserService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandStateService;
import ru.gadjini.telegram.smart.bot.commons.service.file.FileManager;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.service.message.MediaMessageService;
import ru.gadjini.telegram.smart.bot.commons.service.message.MessageService;
import ru.gadjini.telegram.smart.bot.commons.service.queue.QueueService;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;
import ru.gadjini.telegram.unzipper.service.keyboard.InlineKeyboardService;
import ru.gadjini.telegram.unzipper.service.progress.Lang;
import ru.gadjini.telegram.unzipper.service.queue.UnzipQueueService;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class UnzipService {

    private static final String TAG = "unz";

    private final Logger LOGGER = LoggerFactory.getLogger(UnzipService.class);

    private Set<UnzipDevice> unzipDevices;

    private LocalisationService localisationService;

    private MessageService messageService;

    private MediaMessageService mediaMessageService;

    private FileManager fileManager;

    private UnzipQueueService unzipQueueService;

    private QueueService queueService;

    private UserService userService;

    private CommandStateService commandStateService;

    private InlineKeyboardService inlineKeyboardService;

    private UnzipMessageBuilder messageBuilder;

    @Autowired
    public UnzipService(Set<UnzipDevice> unzipDevices, LocalisationService localisationService,
                        @Qualifier("messageLimits") MessageService messageService,
                        @Qualifier("mediaLimits") MediaMessageService mediaMessageService, FileManager fileManager,
                        UnzipQueueService unzipQueueService, QueueService queueService, UserService userService,
                        CommandStateService commandStateService, InlineKeyboardService inlineKeyboardService,
                        UnzipMessageBuilder messageBuilder) {
        this.unzipDevices = unzipDevices;
        this.localisationService = localisationService;
        this.messageService = messageService;
        this.mediaMessageService = mediaMessageService;
        this.fileManager = fileManager;
        this.unzipQueueService = unzipQueueService;
        this.queueService = queueService;
        this.userService = userService;
        this.commandStateService = commandStateService;
        this.inlineKeyboardService = inlineKeyboardService;
        this.messageBuilder = messageBuilder;
    }

    public void extractAll(int userId, int messageId, int unzipJobId, String queryId) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else if (unzipState.getUnzipJobId() != unzipJobId) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else {
            UnzipQueueItem item = unzipQueueService.createExtractAllItem(userId, messageId,
                    unzipState.getFiles().values().stream().map(ZipFileHeader::getSize).mapToLong(i -> i).sum());
            sendStartExtractingAllMessage(item, unzipState.getFiles().size());
        }
    }

    public void extractFile(int userId, int messageId, int unzipJobId, int extractFileId, String queryId) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, false, UnzipState.class);
        if (unzipState == null) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else if (unzipState.getUnzipJobId() != unzipJobId) {
            messageService.sendAnswerCallbackQuery(new AnswerCallbackQuery(
                    queryId,
                    localisationService.getMessage(MessagesProperties.MESSAGE_EXTRACT_FILE_IMPOSSIBLE, userService.getLocaleOrDefault(userId)),
                    true
            ));
            messageService.removeInlineKeyboard(userId, messageId);
        } else {
            if (unzipState.getFilesCache().containsKey(extractFileId)) {
                messageService.sendAnswerCallbackQuery(
                        new AnswerCallbackQuery(
                                queryId,
                                localisationService.getMessage(MessagesProperties.MESSAGE_UNZIP_PROCESSING_ANSWER, userService.getLocaleOrDefault(userId))
                        )
                );
                String fileName = FilenameUtils.getName(unzipState.getFiles().get(extractFileId).getPath());
                mediaMessageService.sendFile(userId, unzipState.getFilesCache().get(extractFileId), fileName);
            } else {
                UnzipQueueItem item = unzipQueueService.createExtractFileItem(userId, messageId,
                        extractFileId, unzipState.getFiles().get(extractFileId).getSize());
                sendStartExtractingFileMessage(item);
            }
        }
    }

    public void nextOrPrev(long chatId, int userId, int messageId, int prevLimit, int offset) {
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);

        Locale locale = userService.getLocaleOrDefault(userId);
        UnzipMessageBuilder.FilesMessage filesList = messageBuilder.getFilesList(unzipState.getFiles(), 0, offset, locale);
        InlineKeyboardMarkup filesListKeyboard = inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(),
                filesList.getLimit(), prevLimit, offset, unzipState.getUnzipJobId(), locale);

        unzipState.setPrevLimit(prevLimit);
        unzipState.setOffset(offset);

        commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);
        try {
            messageService.editMessage(new EditMessageText(chatId, messageId, filesList.getMessage())
                    .setThrowEx(true)
                    .setReplyMarkup(filesListKeyboard));
        } catch (Exception e) {
            messageService.sendMessage(new SendMessage((long) userId, filesList.getMessage())
                    .setReplyMarkup(inlineKeyboardService.getFilesListKeyboard(unzipState.filesIds(), filesList.getLimit(), 0, filesList.getOffset(), unzipState.getUnzipJobId(), locale)));
            if (!(e instanceof TelegramApiException)) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void unzip(int userId, int replyToMessageId, MessageMedia file, Locale locale) {
        checkCandidate(file.getFormat(), locale);
        UnzipQueueItem queueItem = unzipQueueService.createUnzipItem(userId, file);
        UnzipState unzipState = commandStateService.getState(userId, UnzipCommandNames.START_COMMAND_NAME, true, UnzipState.class);
        unzipState.setUnzipJobId(queueItem.getId());
        commandStateService.setState(userId, UnzipCommandNames.START_COMMAND_NAME, unzipState);

        sendStartUnzippingMessage(queueItem, locale, message -> {
            queueItem.setProgressMessageId(message.getMessageId());
            queueService.setProgressMessageId(queueItem.getId(), message.getMessageId());
            queueService.setProgressMessageId(queueItem.getId(), message.getMessageId());
            fileManager.setInputFilePending(userId, replyToMessageId, file.getFileId(), file.getFileSize(), TAG);
        });
    }

    private void sendStartUnzippingMessage(UnzipQueueItem queueItem, Locale locale, Consumer<Message> callback) {
        String message = messageBuilder.buildUnzipProgressMessage(queueItem, UnzipStep.WAITING, Lang.JAVA, locale);
        messageService.sendMessage(new HtmlMessage((long) queueItem.getUserId(), message)
                .setReplyMarkup(inlineKeyboardService.getUnzipProcessingKeyboard(queueItem.getId(), locale)), callback);
    }

    private void sendStartExtractingAllMessage(UnzipQueueItem queueItem, int count) {
        Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
        String message = messageBuilder.buildExtractAllProgressMessage(count, 0, ExtractFileStep.WAITING, queueItem.getExtractFileSize(), Lang.JAVA, locale);
        messageService.editMessage(new EditMessageText((long) queueItem.getUserId(), queueItem.getProgressMessageId(), message)
                .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale)));
    }

    private void sendStartExtractingFileMessage(UnzipQueueItem queueItem) {
        Locale locale = userService.getLocaleOrDefault(queueItem.getUserId());
        String message = messageBuilder.buildExtractFileProgressMessage(queueItem, ExtractFileStep.WAITING, Lang.JAVA, locale);
        messageService.editMessage(new EditMessageText((long) queueItem.getUserId(), queueItem.getProgressMessageId(), message)
                .setReplyMarkup(inlineKeyboardService.getExtractFileProcessingKeyboard(queueItem.getId(), locale)));
    }

    private void checkCandidate(Format format, Locale locale) {
        for (UnzipDevice unzipDevice : unzipDevices) {
            if (unzipDevice.accept(format)) {
                return;
            }
        }

        LOGGER.warn("Candidate not found({})", format);
        throw new UserException(localisationService.getMessage(MessagesProperties.MESSAGE_SUPPORTED_ZIP_FORMATS, locale));
    }
}
