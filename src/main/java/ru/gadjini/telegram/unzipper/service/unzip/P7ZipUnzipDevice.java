package ru.gadjini.telegram.unzipper.service.unzip;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.smart.bot.commons.exception.ProcessException;
import ru.gadjini.telegram.smart.bot.commons.io.SmartTempFile;
import ru.gadjini.telegram.smart.bot.commons.service.ProcessExecutor;
import ru.gadjini.telegram.smart.bot.commons.service.TempFileService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.unzipper.model.ZipFileHeader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class P7ZipUnzipDevice extends BaseUnzipDevice {

    private static final String TAG = "p7unzip";

    private TempFileService tempFileService;

    private ProcessExecutor processExecutor;

    @Autowired
    public P7ZipUnzipDevice(TempFileService tempFileService, ProcessExecutor processExecutor) {
        super(Set.of(Format.ZIP, Format.RAR));
        this.tempFileService = tempFileService;
        this.processExecutor = processExecutor;
    }

    @Override
    public void unzip(int userId, String in, String out, String password) {
        processExecutor.execute(buildUnzipCommand(in, out, password));
    }

    @Override
    public List<ZipFileHeader> getZipFiles(String zipFile, String password) {
        SmartTempFile txt = tempFileService.createTempFile(TAG, "txt");

        try {
            processExecutor.executeWithFile(buildContentsCommand(zipFile, password), txt.getFile().getAbsolutePath());

            String contents = Files.readString(txt.toPath());
            int mainContentStartIndex = contents.indexOf("----------\n") + "----------\n".length();
            String mainContent = contents.substring(mainContentStartIndex);
            String[] fileHeaders = mainContent.split("\n\n");
            List<ZipFileHeader> result = new ArrayList<>();
            for (String fileHeader : fileHeaders) {
                if (!fileHeader.startsWith("Path")) {
                    continue;
                }
                int indexOfFolder = fileHeader.indexOf("Folder = ") + "Folder = ".length();
                String folder = fileHeader.substring(indexOfFolder, indexOfFolder + 1);
                if (folder.equals("+")) {
                    continue;
                }
                int indexOfPath = fileHeader.indexOf("Path = ");
                int endLine = fileHeader.indexOf("\n");
                String path = fileHeader.substring(indexOfPath + "Path = ".length(), endLine);
                ZipFileHeader header = new ZipFileHeader();
                header.setPath(path);

                int indexOfSize = fileHeader.indexOf("Size = ");
                fileHeader = fileHeader.substring(indexOfSize);
                endLine = fileHeader.indexOf("\n");
                String size = fileHeader.substring("Size = ".length(), endLine);
                header.setSize(Long.parseLong(size));

                result.add(header);
            }

            return result;
        } catch (IOException ex) {
            throw new ProcessException(ex);
        } finally {
            txt.smartDelete();
        }
    }

    @Override
    public void unzip(String fileHeader, String archivePath, String out, String password) {
        File listFile = getListFile(fileHeader);
        try {
            processExecutor.executeWithFile(buildUnzipFileCommand(listFile.getAbsolutePath(), archivePath, password), out);
        } finally {
            FileUtils.deleteQuietly(listFile);
        }
    }

    private File getListFile(String fileHeader) {
        try {
            File listFile = File.createTempFile("list", ".txt");
            try (PrintWriter printWriter = new PrintWriter(listFile)) {
                printWriter.print(fileHeader);

                return listFile;
            }
        } catch (IOException e) {
            throw new ProcessException(e);
        }
    }

    private String[] buildContentsCommand(String in, String password) {
        return new String[]{"7z", "l", "-p" + password, "-slt", in};
    }

    private String[] buildUnzipFileCommand(String listFile, String archive, String password) {
        return new String[]{"7z", "e", archive, "-p" + password, "-so", "-y", "@" + listFile};
    }

    private String[] buildUnzipCommand(String in, String out, String password) {
        return new String[]{"7z", "x", in, "-y", "-p" + password, "-o" + out};
    }
}
