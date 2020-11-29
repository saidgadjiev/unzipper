package ru.gadjini.telegram.unzipper.service.unzip;

import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public interface UnzipDevice {

    void unzip(int userId, String in, String out, String password);

    default List<ZipFileHeader> getZipFiles(String zipFile, String password) {
        return Collections.emptyList();
    }

    default void unzip(String fileHeader, String archivePath, String out, String password) throws IOException {
    }

    boolean accept(Format zipFormat);
}
