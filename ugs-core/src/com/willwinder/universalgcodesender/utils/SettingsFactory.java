/*
    Copywrite 2013-2016 Christian Moll, Will Winder, Bob Jones

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.willwinder.universalgcodesender.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.willwinder.universalgcodesender.i18n.Localization;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author moll
 */
public class SettingsFactory {
    private static final Logger logger = Logger.getLogger(SettingsFactory.class.getName());
    private static final String USER_HOME = "user.home";
    private static final String FALSE = "false";
    public static final String SETTINGS_DIRECTORY_NAME = "ugs";
    public static final String PROPERTIES_FILENAME = "UniversalGcodeSender.properties";
    public static final String JSON_FILENAME = "UniversalGcodeSender.json";
    public static final String MAC_LIBRARY = "/Library/Preferences/";

    public static Settings loadSettings() {
        migrateOldSettings();

        // the defaults are now in the settings bean
        Settings out = null;
        File settingsFile = getSettingsFile();

        if (!settingsFile.exists()) {
            out = new Settings();
        } else {
            try {
                //logger.log(Level.INFO, "{0}: {1}", new Object[]{Localization.getString("settings.log.location"), settingsFile});
                logger.log(Level.INFO, "Log location: {0}", settingsFile.getAbsolutePath());
                logger.info("Loading settings.");
                out = new Gson().fromJson(new FileReader(settingsFile), Settings.class);
                // Localized setting not available here.
                //logger.info(Localization.getString("settings.log.loading"));
            } catch (FileNotFoundException ex) {
                //logger.warning(Localization.getString("settings.log.error"));
                logger.warning("Can't load settings, using defaults.");
                Logger.getLogger(SettingsFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        out.finalizeInitialization();
        
        if (out == null) return new Settings();
        return out;
    }

    public static void saveSettings(Settings settings) {
        logger.info(Localization.getString("settings.log.saving"));
        try {
            // Save json file.
            File jsonFile = getSettingsFile();
            try (FileWriter fileWriter = new FileWriter(jsonFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                fileWriter.write(gson.toJson(settings, Settings.class));
            }
         } catch (Exception e) {
            e.printStackTrace();
            logger.warning(Localization.getString("settings.log.saveerror"));
        }
    }

    /**
     * This is public in case other classes need to save settings somewhere.
     */
    public static File getSettingsDirectory() {
        String homeDir = System.getProperty(USER_HOME);
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac")) {
            homeDir = homeDir + MAC_LIBRARY;
        }
        if (!homeDir.endsWith(File.separator)) {
            homeDir = homeDir + File.separator + ".";
        }

        File dir = new File(homeDir + SETTINGS_DIRECTORY_NAME);
        dir.mkdirs();
        return dir;
    }

    /**
     * Convert legacy property file to JSON, move files from top level setting
     * directory to UGS settings directory.
     */
    private static void migrateOldSettings() {
        File newSettingsDir = getSettingsDirectory();
        File oldSettingDir = newSettingsDir.getParentFile();
        File oldPropertyFile = new File(oldSettingDir, PROPERTIES_FILENAME);
        File oldJsonFile = new File(oldSettingDir, JSON_FILENAME);

        // Convert property file in old location to json file in new location.
        if (oldPropertyFile.exists()) {
            try {
                Settings out = new Settings();
                //logger.log(Level.INFO, "{0}: {1}", new Object[]{Localization.getString("settings.log.location"), settingsFile});
                logger.log(Level.INFO, "Log location: {0}", oldPropertyFile.getAbsolutePath());
                Properties properties = new Properties();
                properties.load(new FileInputStream(oldPropertyFile));
                out.setLastOpenedFilename(properties.getProperty("last.dir", System.getProperty(USER_HOME)));
                out.setPort(properties.getProperty("port", ""));
                out.setPortRate(properties.getProperty("port.rate", "9600"));
                out.setManualModeEnabled(Boolean.valueOf(properties.getProperty("manualMode.enabled", FALSE)));
                out.setManualModeStepSize(Double.valueOf(properties.getProperty("manualMode.stepsize", "1")));
                out.setScrollWindowEnabled(Boolean.valueOf(properties.getProperty("scrollWindow.enabled", "true")));
                out.setVerboseOutputEnabled(Boolean.valueOf(properties.getProperty("verboseOutput.enabled", FALSE)));
                out.setOverrideSpeedValue(Double.valueOf(properties.getProperty("overrideSpeed.value", "60")));
                out.setFirmwareVersion(properties.getProperty("firmwareVersion", "GRBL"));
                out.setSingleStepMode(Boolean.valueOf(properties.getProperty("singleStepMode", FALSE)));
                out.setMaxCommandLength(Integer.valueOf(properties.getProperty("maxCommandLength", "50")));
                out.setTruncateDecimalLength(Integer.valueOf(properties.getProperty("truncateDecimalLength", "4")));
                out.setStatusUpdatesEnabled(Boolean.valueOf(properties.getProperty("statusUpdatesEnabled", "true")));
                out.setStatusUpdateRate(Integer.valueOf(properties.getProperty("statusUpdateRate", "200")));
                out.setDisplayStateColor(Boolean.valueOf(properties.getProperty("displayStateColor", "true")));
                out.setSmallArcThreshold(Double.valueOf(properties.getProperty("smallArcThreshold", "2.0")));
                out.setSmallArcSegmentLength(Double.valueOf(properties.getProperty("smallArcSegmentLength", "1.3")));
                out.updateMacro(1, null, null, properties.getProperty("customGcode1", "G0 X0 Y0;"));
                out.updateMacro(2, null, null, properties.getProperty("customGcode2", "G0 G91 X10;G0 G91 Y10;"));
                out.updateMacro(3, null, null, properties.getProperty("customGcode3", ""));
                out.updateMacro(4, null, null, properties.getProperty("customGcode4", ""));
                out.updateMacro(5, null, null, properties.getProperty("customGcode5", ""));
                out.setLanguage(properties.getProperty("language", "en_US"));
                saveSettings(out);

                // Delete the old settings file if it exists.
                oldPropertyFile.delete();
            } catch (IOException ex) {
                Logger.getLogger(SettingsFactory.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        // Move old json file from old location to new location.
        else if (oldJsonFile.exists()) {
            try {
                // If the new file doesn't exist, move the old one.
                if (!getSettingsFile().exists()) {
                    FileUtils.moveFile(oldJsonFile, getSettingsFile());
                }
                // Delete the old settings file if it exists.
                oldJsonFile.delete();
            } catch (IOException ex) {
                Logger.getLogger(SettingsFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static File getSettingsFile() {
        File settingDir = SettingsFactory.getSettingsDirectory();
        File json = new File (settingDir, JSON_FILENAME);
        return json;
    }
}
