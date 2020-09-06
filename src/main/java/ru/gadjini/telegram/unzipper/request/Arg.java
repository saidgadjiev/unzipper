package ru.gadjini.telegram.unzipper.request;

import ru.gadjini.telegram.smart.bot.commons.command.impl.CallbackDelegate;

public enum Arg {

    CALLBACK_DELEGATE(CallbackDelegate.ARG_NAME),
    EXTRACT_FILE_ID("r"),
    JOB_ID("s"),
    OFFSET("t"),
    PAGINATION("u"),
    PREV_LIMIT("v");

    private final String key;

    Arg(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
