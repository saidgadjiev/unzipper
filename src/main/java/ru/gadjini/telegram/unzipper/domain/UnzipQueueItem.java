package ru.gadjini.telegram.unzipper.domain;

import ru.gadjini.telegram.smart.bot.commons.domain.DownloadQueueItem;
import ru.gadjini.telegram.smart.bot.commons.domain.TgFile;
import ru.gadjini.telegram.smart.bot.commons.domain.WorkQueueItem;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;

import java.io.File;
import java.util.Set;

public class UnzipQueueItem extends WorkQueueItem {

    public static final String NAME = "unzip_queue";

    public static final String FILE = "file";

    public static final String TYPE = "type";

    public static final String ITEM_TYPE = "item_type";

    public static final String EXTRACT_FILE_ID = "extract_file_id";

    public static final String EXTRACT_FILE_SIZE = "extract_file_size";

    public static final String DOWNLOADS = "downloads";

    private TgFile file;

    private Format type;

    private ItemType itemType;

    private int extractFileId;

    private long extractFileSize;

    private DownloadQueueItem download;

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

    public long getExtractFileSize() {
        return extractFileSize;
    }

    public void setExtractFileSize(long extractFileSize) {
        this.extractFileSize = extractFileSize;
    }

    public DownloadQueueItem getDownload() {
        return download;
    }

    public void setDownload(DownloadQueueItem download) {
        this.download = download;
    }

    public SmartTempFile getDownloadedFile() {
        return download == null ? null : new SmartTempFile(new File(download.getFilePath()), download.isDeleteParentDir());
    }

    @Override
    public long getSize() {
        return Set.of(ItemType.EXTRACT_ALL, ItemType.EXTRACT_FILE).contains(itemType) ? extractFileSize : file.getSize();
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
