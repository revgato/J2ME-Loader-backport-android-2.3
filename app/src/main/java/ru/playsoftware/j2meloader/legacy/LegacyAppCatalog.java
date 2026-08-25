/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Core catalog contract used by the platform-only launcher. */
public interface LegacyAppCatalog {
    List<Game> scan() throws IOException;

    final class Game {
        private final String directoryName;
        private final String name;
        private final String vendor;
        private final String version;
        private final String iconEntry;
        private final File directory;

        public Game(String directoryName, String name, String vendor, String version, File directory) {
            this(directoryName, name, vendor, version, null, directory);
        }

        public Game(String directoryName, String name, String vendor, String version,
                    String iconEntry, File directory) {
            this.directoryName = directoryName;
            this.name = name;
            this.vendor = vendor;
            this.version = version;
            this.iconEntry = iconEntry;
            this.directory = directory;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public String getName() {
            return name;
        }

        public String getVendor() {
            return vendor;
        }

        public String getVersion() {
            return version;
        }

        /** Normalized path of the icon inside the installed resource JAR, or null. */
        public String getIconEntry() {
            return iconEntry;
        }

        public File getDirectory() {
            return directory;
        }
    }
}
