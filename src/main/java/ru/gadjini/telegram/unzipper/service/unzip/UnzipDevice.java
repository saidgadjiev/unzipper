package ru.gadjini.telegram.unzipper.service.unzip;

import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public interface UnzipDevice {

    void unzip(int userId, String in, String out);

    default List<ZipFileHeader> getZipFiles(String zipFile) {
        return Collections.emptyList();
    }

    default void unzip(String fileHeader, String archivePath, String out) throws IOException {
    }

    boolean accept(Format zipFormat);
}
