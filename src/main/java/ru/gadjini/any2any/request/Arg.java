package ru.gadjini.any2any.request;

public enum Arg {

    CALLBACK_DELEGATE("d"),
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
