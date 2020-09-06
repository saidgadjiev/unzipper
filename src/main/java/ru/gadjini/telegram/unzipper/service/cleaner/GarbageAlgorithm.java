package ru.gadjini.telegram.unzipper.service.cleaner;

import java.io.File;

public interface GarbageAlgorithm {

    boolean accept(File file);

    boolean isGarbage(File file);
}
