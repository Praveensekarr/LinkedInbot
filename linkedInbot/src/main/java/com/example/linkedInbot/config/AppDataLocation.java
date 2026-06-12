package com.example.linkedInbot.config;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class AppDataLocation {

    private static final String FOLDER_NAME = "LinkedInBotData";
    private static final String ENV_OVERRIDE = "LINKEDINBOT_DATA_DIR";

    private static volatile File dataDir;

    public static synchronized File getDataDir() {
        if (dataDir != null) {
            return dataDir;
        }

        String override = System.getenv(ENV_OVERRIDE);
        File dir;
        if (override != null && !override.isBlank()) {
            dir = new File(override);
        } else {
            String userHome = System.getProperty("user.home");
            dir = new File(userHome, FOLDER_NAME);
        }

        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("Created external data directory at {}", dir.getAbsolutePath());
            } else {
                log.warn("Could not create external data directory at {}", dir.getAbsolutePath());
            }
        }

        dataDir = dir;
        return dataDir;
    }

    public static File resolve(String fileName) {
        return new File(getDataDir(), fileName);
    }
}
