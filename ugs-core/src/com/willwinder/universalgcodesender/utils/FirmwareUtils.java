/*
 * FirmwareUtils.java
 *
 * Created on April 2, 2013
 */

/*
    Copywrite 2012-2014 Will Winder

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
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.willwinder.universalgcodesender.AbstractController;
import com.willwinder.universalgcodesender.gcode.processors.ICommandProcessor;
import com.willwinder.universalgcodesender.gcode.processors.PatternRemover;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import sun.misc.Launcher;

/**
 *
 * @author wwinder
 */
public class FirmwareUtils {
    private static final Logger logger = Logger.getLogger(FirmwareUtils.class.getName());
    final private static String FIRMWARE_CONFIG_DIRNAME = "firmware_config";

    /**
     * Need a simple way to map the config loader (JSON in POJO format) to the
     * file it was generated from.
     */
    public static class ConfigTuple {
        public ControllerSettings loader;
        public File file;
        public ConfigTuple(ControllerSettings l, File f) {
            this.loader = l;
            this.file = f;
        }
        public void reload() {
            try {
                loader = new Gson().fromJson(new FileReader(file), ControllerSettings.class);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(FirmwareUtils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static Map<String,ConfigTuple> configFiles = new HashMap<>();

    public static Map<String,ConfigTuple> getConfigFiles() {
        return configFiles;
    }

    static {
        initialize();
    }
    
    public static ArrayList<String> getFirmwareList() {
        ArrayList<String> ret = new ArrayList<>();
        for (String fw : configFiles.keySet()) {
            ret.add(fw);
        }
        return ret;
    }
    
    /**
     * Gets a list of command processors initialized with user settings.
     */
    public static Optional<List<ICommandProcessor>> getParserFor(String firmware, Settings settings) {
        if (!configFiles.containsKey(firmware)) {
            return Optional.empty();
        }
        return Optional.of(configFiles.get(firmware).loader.getProcessors(settings));
    }

    public static void addPatternRemoverForFirmware(String firmware, String pattern) throws IOException {
        if (!configFiles.containsKey(firmware)) {
            return;
        }
        ConfigTuple tuple = configFiles.get(firmware);
        JsonObject args = new JsonObject();
        args.addProperty("pattern", pattern);
        tuple.loader.GcodeProcessors.Custom.add(
                new ControllerSettings.ProcessorConfig("PatternRemover",
                        true, true, args));
        save(tuple.file, tuple.loader);
    }

    /**
     * Gets a new controller object from a firmware config.
     * @param firmware
     * @return 
     */
    public static Optional<AbstractController> getControllerFor(String firmware) {
        if (!configFiles.containsKey(firmware)) {
            return Optional.empty();
        }

        /*
        ConfigLoader config = new Gson().fromJson(new FileReader(configFiles.get(firmware).configFile), ConfigLoader.class);
        File f = configFiles.get(firmware).configFile;
        File next = new File(f.getParent(), f.getName() + ".out");
        try (FileWriter fileWriter = new FileWriter(next)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
             fileWriter.write(gson.toJson(config, ConfigLoader2.class));
        } catch (IOException ex) {
            Logger.getLogger(FirmwareUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Have config: " + config.toString());
        */
        return Optional.of(configFiles.get(firmware).loader.getController());
    }

    /**
     * Deletes firmware_config file from the machine then recreate it.
     */
    public static void restoreDefaults(String firmware) throws IOException {
        ConfigTuple tuple = configFiles.get(firmware);
        configFiles.remove(firmware);
        tuple.file.delete();

        // Reload missing file.
        initialize();
    }

    /**
     * Copy any missing files from the the jar's resources/firmware_config/ dir
     * into the settings/firmware_config dir.
     */
    public static void initialize() {
        File firmwareConfig = new File(SettingsFactory.getSettingsDirectory(),
                FIRMWARE_CONFIG_DIRNAME);

        // Create directory if it's missing.
        if (!firmwareConfig.exists()) {
            firmwareConfig.mkdirs();
        }

        // Copy firmware config files.
        try {
            final String dir = "resources/firmware_config/";
            final File jarFile = new File(FirmwareUtils.class
                    .getProtectionDomain().getCodeSource().getLocation().getPath());

            // Extract file from .jar
            if(jarFile.isFile()) {
                final JarFile jar = new JarFile(jarFile);
                //gives ALL entries in jar
                final Enumeration<JarEntry> entries = jar.entries();
                while(entries.hasMoreElements()) {
                    final String name = entries.nextElement().getName();
                    //filter according to the path
                    if (name.startsWith(dir)) {
                        String entry = name.substring(dir.length());
                        File fwConfig = new File(firmwareConfig, entry);
                        if (!fwConfig.exists()) {
                            InputStream is = FirmwareUtils.class.getClassLoader().
                                    getResourceAsStream(name);
                            FileUtils.copyInputStreamToFile(is, fwConfig);
                        }
                    }
                }
                jar.close();
            }
            // Extract files from IDE
            else {
                List<String> files = IOUtils.readLines(FirmwareUtils.
                        class.getClassLoader()
                        .getResourceAsStream(dir), Charsets.UTF_8);

                // Create any files which don't exist.
                for (String file : files) {
                    File fwConfig = new File(firmwareConfig, file);
                    if (!fwConfig.exists()) {
                        InputStream is = FirmwareUtils.class.getClassLoader().
                                getResourceAsStream(dir + file);
                        FileUtils.copyInputStreamToFile(is, new File(firmwareConfig, file));
                    }
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(FirmwareUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

        configFiles.clear();
        for (File f : firmwareConfig.listFiles()) {
            try {
                ControllerSettings config = new Gson().fromJson(new FileReader(f), ControllerSettings.class);
                //ConfigLoader config = new ConfigLoader(f);
                configFiles.put(config.getName(), new ConfigTuple(config, f));
            } catch (FileNotFoundException | JsonSyntaxException | JsonIOException ex) {
                GUIHelpers.displayErrorDialog("Unable to load configuration files: " + f.getAbsolutePath());
            }
        }
    }

    public static void save(File f, ControllerSettings cs) throws IOException {
        if (f.exists()) {
            f.delete();
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(cs, ControllerSettings.class);
        FileUtils.writeStringToFile(f, json);
    }
}