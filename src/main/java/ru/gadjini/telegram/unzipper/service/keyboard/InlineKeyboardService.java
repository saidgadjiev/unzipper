package ru.gadjini.telegram.unzipper.service.keyboard;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.InlineKeyboardMarkup;
import ru.gadjini.telegram.unzipper.common.CommandNames;
import ru.gadjini.telegram.smart.bot.commons.model.bot.api.object.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InlineKeyboardService {

    private ButtonFactory buttonFactory;

    @Autowired
    public InlineKeyboardService(ButtonFactory buttonFactory) {
        this.buttonFactory = buttonFactory;
    }

    public InlineKeyboardMarkup getFilesListKeyboard(Set<Integer> filesIds, int limit, int prevLimit, int offset, int unzipJobId, Locale locale) {
        InlineKeyboardMarkup inlineKeyboardMarkup = inlineKeyboardMarkup();

        if (!(offset == 0 && filesIds.size() == limit)) {
            if (filesIds.size() == offset + limit) {
                inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.toPrevPage(CommandNames.START_COMMAND_NAME, limit, Math.max(0, offset - prevLimit), locale)));
            } else if (offset == 0) {
                inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.toNextPage(CommandNames.START_COMMAND_NAME, limit, offset + limit, locale)));
            } else {
                inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.toPrevPage(CommandNames.START_COMMAND_NAME, limit, Math.max(0, offset - prevLimit), locale),
                        buttonFactory.toNextPage(CommandNames.START_COMMAND_NAME, limit, offset + limit, locale)));
            }
        }
        List<List<Integer>> lists = Lists.partition(filesIds.stream().skip(offset).limit(limit).collect(Collectors.toCollection(ArrayList::new)), 4);
        int i = offset + 1;
        for (List<Integer> list : lists) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (int id : list) {
                row.add(buttonFactory.extractFileButton(String.valueOf(i++), id, unzipJobId));
            }

            inlineKeyboardMarkup.getKeyboard().add(row);
        }
        inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.extractAllButton(unzipJobId, locale)));

        return inlineKeyboardMarkup;
    }

    public InlineKeyboardMarkup getUnzipProcessingKeyboard(int jobId, Locale locale) {
        InlineKeyboardMarkup inlineKeyboardMarkup = inlineKeyboardMarkup();

        inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.cancelUnzipQuery(jobId, locale)));

        return inlineKeyboardMarkup;
    }

    public InlineKeyboardMarkup getExtractFileProcessingKeyboard(int jobId, Locale locale) {
        InlineKeyboardMarkup inlineKeyboardMarkup = inlineKeyboardMarkup();

        inlineKeyboardMarkup.getKeyboard().add(List.of(buttonFactory.cancelExtractFileQuery(jobId, locale)));

        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup inlineKeyboardMarkup() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        inlineKeyboardMarkup.setKeyboard(new ArrayList<>());

        return inlineKeyboardMarkup;
    }
}
