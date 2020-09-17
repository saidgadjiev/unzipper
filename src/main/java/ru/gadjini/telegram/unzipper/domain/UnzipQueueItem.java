package ru.gadjini.telegram.unzipper.domain;

import ru.gadjini.telegram.smart.bot.commons.domain.TgFile;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;

public class UnzipQueueItem {

    public static final String NAME = "unzip_queue";

    public static final String ID = "id";

    public static final String USER_ID = "user_id";

    public static final String FILE = "file";

    public static final String TYPE = "type";

    public static final String STATUS = "status";

    public static final String ITEM_TYPE = "item_type";

    public static final String EXTRACT_FILE_ID = "extract_file_id";

    public static final String EXTRACT_FILE_SIZE = "extract_file_size";

    public static final String MESSAGE_ID = "message_id";

    private int id;

    private int userId;

    private TgFile file;

    private Format type;

    private Status status;

    private ItemType itemType;

    private Integer messageId;

    private int extractFileId;

    private long extractFileSize;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public TgFile getFile() {
        return file;
    }

    public void setFile(TgFile file) {
        this.file = file;
    }

    public Format getType() {
        return type;
    }

    public void setType(Format type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public int getExtractFileId() {
        return extractFileId;
    }

    public void setExtractFileId(int extractFileId) {
        this.extractFileId = extractFileId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public long getExtractFileSize() {
        return extractFileSize;
    }

    public void setExtractFileSize(long extractFileSize) {
        this.extractFileSize = extractFileSize;
    }

    public enum Status {

        WAITING(0),

        PROCESSING(1);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public enum ItemType {

        UNZIP(0),

        EXTRACT_FILE(1),

        EXTRACT_ALL(2);

        private final int code;

        ItemType(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static ItemType fromCode(int code) {
            for (ItemType itemType : values()) {
                if (itemType.code == code) {
                    return itemType;
                }
            }
            throw new IllegalArgumentException("Unknown queue item status " + code);
        }
    }
}
