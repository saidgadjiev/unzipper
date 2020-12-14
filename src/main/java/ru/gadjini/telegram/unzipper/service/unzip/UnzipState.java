package ru.gadjini.telegram.unzipper.service.unzip;

import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UnzipState {

    private String archivePath;

    private Format archiveType;

    private int unzipJobId;

    private final Map<Integer, ZipFileHeader> files = new LinkedHashMap<>();

    private final Map<Integer, String> filesCache = new HashMap<>();

    private int prevLimit;

    private int offset;

    private String password;

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public Set<Integer> filesIds() {
        return files.keySet();
    }

    public Format getArchiveType() {
        return archiveType;
    }

    public void setArchiveType(Format archiveType) {
        this.archiveType = archiveType;
    }

    public Map<Integer, ZipFileHeader> getFiles() {
        return files;
    }

    public Map<Integer, String> getFilesCache() {
        return filesCache;
    }

    public int getUnzipJobId() {
        return unzipJobId;
    }

    public void setUnzipJobId(int unzipJobId) {
        this.unzipJobId = unzipJobId;
    }

    public int getPrevLimit() {
        return prevLimit;
    }

    public void setPrevLimit(int prevLimit) {
        this.prevLimit = prevLimit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
