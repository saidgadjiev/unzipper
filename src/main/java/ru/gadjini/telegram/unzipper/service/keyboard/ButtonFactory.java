package ru.gadjini.telegram.unzipper.service.keyboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.common.CommandNames;
import ru.gadjini.telegram.smart.bot.commons.service.LocalisationService;
import ru.gadjini.telegram.smart.bot.commons.service.command.CommandParser;
import ru.gadjini.telegram.smart.bot.commons.service.request.RequestParams;
import ru.gadjini.telegram.unzipper.common.UnzipCommandNames;
import ru.gadjini.telegram.unzipper.common.MessagesProperties;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.buttons.InlineKeyboardButton;
import ru.gadjini.telegram.unzipper.request.Arg;

import java.util.Locale;

@Service
public class ButtonFactory {

    private LocalisationService localisationService;

    @Autowired
    public ButtonFactory(LocalisationService localisationService) {
        this.localisationService = localisationService;
    }

    public InlineKeyboardButton toNextPage(String delegate, int prevLimit, int offset, Locale locale) {
        InlineKeyboardButton button = new InlineKeyboardButton(localisationService.getMessage(MessagesProperties.MESSAGE_NEXT_PAGE, locale));
        button.setCallbackData(CommandNames.CALLBACK_DELEGATE_COMMAND_NAME + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams()
                        .add(Arg.OFFSET.getKey(), offset)
                        .add(Arg.PREV_LIMIT.getKey(), prevLimit)
                        .add(Arg.PAGINATION.getKey(), true)
                        .add(Arg.CALLBACK_DELEGATE.getKey(), delegate)
                        .serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return button;
    }

    public InlineKeyboardButton toPrevPage(String delegate, int prevLimit, int offset, Locale locale) {
        InlineKeyboardButton button = new InlineKeyboardButton(localisationService.getMessage(MessagesProperties.MESSAGE_PREV_PAGE, locale));
        button.setCallbackData(CommandNames.CALLBACK_DELEGATE_COMMAND_NAME + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams()
                        .add(Arg.OFFSET.getKey(), offset)
                        .add(Arg.PREV_LIMIT.getKey(), prevLimit)
                        .add(Arg.PAGINATION.getKey(), true)
                        .add(Arg.CALLBACK_DELEGATE.getKey(), delegate)
                        .serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return button;
    }

    public InlineKeyboardButton cancelUnzipQuery(int jobId, Locale locale) {
        InlineKeyboardButton button = new InlineKeyboardButton(localisationService.getMessage(MessagesProperties.CANCEL_COMMAND_DESCRIPTION, locale));
        button.setCallbackData(UnzipCommandNames.CANCEL_UNZIP_QUERY + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams().add(Arg.JOB_ID.getKey(), jobId).serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return button;
    }

    public InlineKeyboardButton cancelExtractFileQuery(int jobId, Locale locale) {
        InlineKeyboardButton button = new InlineKeyboardButton(localisationService.getMessage(MessagesProperties.CANCEL_COMMAND_DESCRIPTION, locale));
        button.setCallbackData(UnzipCommandNames.CANCEL_EXTRACT_FILE + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams().add(Arg.JOB_ID.getKey(), jobId).serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return button;
    }

    public InlineKeyboardButton extractAllButton(int unzipJobId, Locale locale) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton(localisationService.getMessage(MessagesProperties.EXTRACT_ALL_COMMAND_DESCRIPTION, locale));
        inlineKeyboardButton.setCallbackData(UnzipCommandNames.EXTRACT_ALL + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams()
                        .add(Arg.JOB_ID.getKey(), unzipJobId)
                        .serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return inlineKeyboardButton;
    }

    public InlineKeyboardButton extractFileButton(String name, int id, int unzipJobId) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton(name);
        inlineKeyboardButton.setCallbackData(UnzipCommandNames.EXTRACT_FILE_COMMAND_NAME + CommandParser.COMMAND_NAME_SEPARATOR +
                new RequestParams()
                        .add(Arg.EXTRACT_FILE_ID.getKey(), id)
                        .add(Arg.JOB_ID.getKey(), unzipJobId)
                        .serialize(CommandParser.COMMAND_ARG_SEPARATOR));

        return inlineKeyboardButton;
    }
}
