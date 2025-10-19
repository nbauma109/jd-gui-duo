/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.configuration;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.Constants;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.service.platform.PlatformService;
import org.jd.gui.util.ThemeUtil;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import static org.jd.gui.util.decompiler.GuiPreferences.ERROR_BACKGROUND_COLOR;
import static jd.core.preferences.Preferences.JD_CORE_VERSION;

public class ConfigurationXmlPersisterProvider implements ConfigurationPersister {
    private static final String NEW_LINE_2_TABS = "\n\t\t";
    private static final String NEW_LINE_3_TABS = "\n\t\t\t";
    protected static final File FILE = getConfigFile();

    protected static File getConfigFile() {
        String configFilePath = System.getProperty(Constants.CONFIG_FILENAME);

        if (configFilePath != null) {
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                return configFile;
            }
        }

        if (PlatformService.getInstance().isLinux()) {
            // See: http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                File xdgConfigHomeFile = new File(xdgConfigHome);
                if (xdgConfigHomeFile.exists()) {
                    return new File(xdgConfigHomeFile, Constants.CONFIG_FILENAME);
                }
            }

            File userConfigFile = new File(System.getProperty("user.home"), ".config");
            if (userConfigFile.exists()) {
                return new File(userConfigFile, Constants.CONFIG_FILENAME);
            }
        } else if (PlatformService.getInstance().isWindows()) {
            // See: http://blogs.msdn.com/b/patricka/archive/2010/03/18/where-should-i-store-my-data-and-configuration-files-if-i-target-multiple-os-versions.aspx
            String roamingConfigHome = System.getenv("APPDATA");
            if (roamingConfigHome != null) {
                File roamingConfigHomeFile = new File(roamingConfigHome);
                if (roamingConfigHomeFile.exists()) {
                    return new File(roamingConfigHomeFile, Constants.CONFIG_FILENAME);
                }
            }
        }

        return new File(Constants.CONFIG_FILENAME);
    }

    @Override
    public Configuration load() {
        // Default values
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        int w = screenSize.width>Constants.DEFAULT_WIDTH ? Constants.DEFAULT_WIDTH : screenSize.width;
        int h = screenSize.height>Constants.DEFAULT_HEIGHT ? Constants.DEFAULT_HEIGHT : screenSize.height;
        int x = (screenSize.width-w)/2;
        int y = (screenSize.height-h)/2;

        Configuration config = new Configuration();
        config.setMainWindowLocation(new Point(x, y));
        config.setMainWindowSize(new Dimension(w, h));
        config.setMainWindowMaximize(false);

        config.setLookAndFeel(ThemeUtil.getDefaultLookAndFeel());

        File recentSaveDirectory = new File(System.getProperty("user.dir"));

        config.setRecentLoadDirectory(recentSaveDirectory);
        config.setRecentSaveDirectory(recentSaveDirectory);

        if (FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(FILE)) {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                XMLStreamReader reader = factory.createXMLStreamReader(fis);

                // Load values
                String name = "";
                Deque<String> names = new ArrayDeque<>();
                List<File> recentFiles = new ArrayList<>();
                boolean maximize = false;
                Map<String, String> preferences = config.getPreferences();

                while (reader.hasNext()) {
                    switch (reader.next()) {
                        case XMLStreamConstants.START_ELEMENT:
                            names.push(name);
                            name = String.join("/", name, reader.getLocalName());
                            if ("/configuration/gui/mainWindow/location".equals(name)) {
                                x = Integer.parseInt(reader.getAttributeValue(null, "x"));
                                y = Integer.parseInt(reader.getAttributeValue(null, "y"));
                            } else if ("/configuration/gui/mainWindow/size".equals(name)) {
                                w = Integer.parseInt(reader.getAttributeValue(null, "w"));
                                h = Integer.parseInt(reader.getAttributeValue(null, "h"));
                            }
                            break;
                        case XMLStreamConstants.END_ELEMENT:
                            name = names.pop();
                            break;
                        case XMLStreamConstants.CHARACTERS:
                            switch (name) {
                                case "/configuration/recentFilePaths/filePath":
                                    File file = new File(reader.getText().trim());
                                    if (file.exists()) {
                                        recentFiles.add(file);
                                    }
                                    break;
                                case "/configuration/recentDirectories/loadPath":
                                    file = new File(reader.getText().trim());
                                    if (file.exists()) {
                                        config.setRecentLoadDirectory(file);
                                    }
                                    break;
                                case "/configuration/recentDirectories/savePath":
                                    file = new File(reader.getText().trim());
                                    if (file.exists()) {
                                        config.setRecentSaveDirectory(file);
                                    }
                                    break;
                                case "/configuration/gui/lookAndFeel":
                                    config.setLookAndFeel(reader.getText().trim());
                                    break;
                                case "/configuration/gui/mainWindow/maximize":
                                    maximize = Boolean.parseBoolean(reader.getText().trim());
                                    break;
                                default:
                                    if (name.startsWith("/configuration/preferences/")) {
                                        String key = name.substring("/configuration/preferences/".length());
                                        String text = reader.getText();
                                        if (text.isBlank()) { // example: indentation property => don't trim
                                            preferences.put(key, text);
                                        } else {
                                            preferences.put(key, text.trim());
                                        }
                                    }
                                    break;
                            }
                            break;
                    }
                }

                if (recentFiles.size() > Constants.MAX_RECENT_FILES) {
                    // Truncate
                    recentFiles = recentFiles.subList(0, Constants.MAX_RECENT_FILES);
                }
                config.setRecentFiles(recentFiles);

                if (x >= 0 && y >= 0 && x + w < screenSize.width && y + h < screenSize.height) {
                    // Update preferences
                    config.setMainWindowLocation(new Point(x, y));
                    config.setMainWindowSize(new Dimension(w, h));
                    config.setMainWindowMaximize(maximize);
                }

                reader.close();
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        if (! config.getPreferences().containsKey(ERROR_BACKGROUND_COLOR)) {
            config.getPreferences().put(ERROR_BACKGROUND_COLOR, "0xFF6666");
        }

        config.getPreferences().put(JD_CORE_VERSION, getJdCoreVersion());

        return config;
    }

    protected String getJdCoreVersion() {
        try {
            Enumeration<URL> enumeration = ConfigurationXmlPersisterProvider.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

            while (enumeration.hasMoreElements()) {
                try (InputStream is = enumeration.nextElement().openStream()) {
                    String attribute = new Manifest(is).getMainAttributes().getValue("JD-Core-Version");
                    if (attribute != null) {
                        return attribute;
                    }
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return "SNAPSHOT";
    }

    @Override
    public void save(Configuration configuration) {
        Point l = configuration.getMainWindowLocation();
        Dimension s = configuration.getMainWindowSize();

        try (FileOutputStream fos = new FileOutputStream(FILE)) {
            XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(fos);

            // Save values
            writer.writeStartDocument();
            writer.writeCharacters("\n");
            writer.writeStartElement("configuration");
            writer.writeCharacters("\n\t");

            writer.writeStartElement("gui");
            writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement("mainWindow");
                writer.writeCharacters(NEW_LINE_3_TABS);
                    writer.writeStartElement("location");
                        writer.writeAttribute("x", String.valueOf(l.x));
                        writer.writeAttribute("y", String.valueOf(l.y));
                    writer.writeEndElement();
                    writer.writeCharacters(NEW_LINE_3_TABS);
                    writer.writeStartElement("size");
                        writer.writeAttribute("w", String.valueOf(s.width));
                        writer.writeAttribute("h", String.valueOf(s.height));
                    writer.writeEndElement();
                    writer.writeCharacters(NEW_LINE_3_TABS);
                    writer.writeStartElement("maximize");
                        writer.writeCharacters(String.valueOf(configuration.isMainWindowMaximize()));
                    writer.writeEndElement();
                    writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeEndElement();
                writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement("lookAndFeel");
                    writer.writeCharacters(configuration.getLookAndFeel());
                writer.writeEndElement();
                writer.writeCharacters("\n\t");
            writer.writeEndElement();
            writer.writeCharacters("\n\t");

            writer.writeStartElement("recentFilePaths");

            for (File recentFile : configuration.getRecentFiles()) {
                writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement("filePath");
                    writer.writeCharacters(recentFile.getAbsolutePath());
                writer.writeEndElement();
            }

            writer.writeCharacters("\n\t");
            writer.writeEndElement();
            writer.writeCharacters("\n\t");

            writer.writeStartElement("recentDirectories");
            writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement("loadPath");
                    writer.writeCharacters(configuration.getRecentLoadDirectory().getAbsolutePath());
                writer.writeEndElement();
                writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement("savePath");
                    writer.writeCharacters(configuration.getRecentSaveDirectory().getAbsolutePath());
                writer.writeEndElement();
                writer.writeCharacters("\n\t");
            writer.writeEndElement();
            writer.writeCharacters("\n\t");

            writer.writeStartElement("preferences");

            for (Map.Entry<String, String> preference : configuration.getPreferences().entrySet()) {
                writer.writeCharacters(NEW_LINE_2_TABS);
                writer.writeStartElement(preference.getKey());
                    writer.writeCharacters(preference.getValue());
                writer.writeEndElement();
            }

            writer.writeCharacters("\n\t");
            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
