package ru.gadjini.any2any.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.any2any.common.CommandNames;
import ru.gadjini.any2any.common.MessagesProperties;

import java.util.Locale;

@Service
public class CommandMessageBuilder {

    private LocalisationService localisationService;

    @Autowired
    public CommandMessageBuilder(LocalisationService localisationService) {
        this.localisationService = localisationService;
    }

    public String getCommandsInfo(Locale locale) {
        StringBuilder info = new StringBuilder();

        info.append("/").append(CommandNames.START_COMMAND).append(" - ").append(localisationService.getMessage(MessagesProperties.START_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.IMAGE_EDITOR_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.IMAGE_EDITOR_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.CONVERT_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.CONVERT_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.QUERIES_COMMAND).append(" - ").append(localisationService.getMessage(MessagesProperties.QUERIES_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.RENAME_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.RENAME_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.OCR_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.OCR_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.UNZIP_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.UNZIP_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.ARCHIVE_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.ARCHIVE_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.LANGUAGE_COMMAND_NAME).append(" - ").append(localisationService.getMessage(MessagesProperties.LANGUAGE_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.FORMATS_COMMAND).append(" - ").append(localisationService.getMessage(MessagesProperties.FORMATS_COMMAND_DESCRIPTION, locale)).append("\n");
        info.append("/").append(CommandNames.HELP_COMMAND).append(" - ").append(localisationService.getMessage(MessagesProperties.HELP_COMMAND_DESCRIPTION, locale));

        return info.toString();
    }
}
