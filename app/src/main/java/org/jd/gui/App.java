/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui;

import org.apache.commons.lang3.Strings;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.controller.MainController;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.service.configuration.ConfigurationPersister;
import org.jd.gui.service.configuration.ConfigurationPersisterService;
import org.jd.gui.util.net.InterProcessCommunicationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.awt.GraphicsEnvironment;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

public class App {

	protected static final String SINGLE_INSTANCE = "UIMainWindowPreferencesProvider.singleInstance";
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class.getName());

    private static final String HELP = """
		Usage: jd-gui-duo [option] [input-file] ...

		Option:
		 --help/-h         Show this help message and exit
		 --version/-v      Show version information and exit""";

    protected static MainController controller;

    public static void main(String[] args) {
        if (checkFlag(args, "--help", "-h")) {
            showUserMessage(HELP);
        } else if (checkFlag(args, "--version", "-v")) {
            showUserMessage(buildVersionMessage());
        } else {
            // Load preferences
            ConfigurationPersister persister = ConfigurationPersisterService.getInstance().get();
            Configuration configuration = persister.load();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> persister.save(configuration)));

            if ("true".equals(configuration.getPreferences().get(SINGLE_INSTANCE))) {
                try {
                    InterProcessCommunicationUtil.listen(receivedArgs -> controller.openFiles(newList(receivedArgs)));
                } catch (Exception notTheFirstInstanceException) {
                    assert ExceptionUtil.printStackTrace(notTheFirstInstanceException);
                    // Send args to main windows and exit
                    InterProcessCommunicationUtil.send(args);
                    System.exit(0);
                }
            }

            // Create SwingBuilder, set look and feel
            try {
                UIManager.setLookAndFeel(configuration.getLookAndFeel());
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
                configuration.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                try {
                    UIManager.setLookAndFeel(configuration.getLookAndFeel());
                } catch (Exception ee) {
                    assert ExceptionUtil.printStackTrace(ee);
                }
           }

            // Create main controller and show main frame
            controller = new MainController(configuration);
            controller.show(newList(args));
        }
    }

    protected static boolean checkFlag(String[] args, String... switches) {
        if (args != null) {
            for (String arg : args) {
                if (Strings.CS.equalsAny(arg, switches)) {
                    return true;
                }
            }
        }
        return false;
    }


    protected static boolean checkHelpFlag(String[] args) {
        return checkFlag(args, "--help", "-h");
    }

    protected static boolean checkVersionFlag(String[] args) {
        return checkFlag(args, "--version", "-v");
    }

    protected static String buildVersionMessage() {
        return Constants.APP_NAME + " " + getVersion();
    }

    protected static void showUserMessage(String message) {
        if (System.console() != null || GraphicsEnvironment.isHeadless()) {
            LOGGER.info("{}", message);
            return;
        }

        JOptionPane.showMessageDialog(null, message, Constants.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    protected static String getVersion() {
        try {
            Enumeration<URL> enumeration = App.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (enumeration.hasMoreElements()) {
                try (InputStream is = enumeration.nextElement().openStream()) {
                    Attributes attributes = new Manifest(is).getMainAttributes();
                    if (attributes != null) {
                        String version = attributes.getValue("JD-GUI-Version");
                        if (version != null) {
                            return version;
                        }
                    }
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return "Unknown";
    }

    protected static List<File> newList(String[] paths) {
        if (paths == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>(paths.length);
        for (String rawPath : paths) {
            if (rawPath != null && rawPath.startsWith("-")) {
                continue;
            }
            File validatedFile = validatePath(rawPath);

            if (validatedFile != null) {
                files.add(validatedFile);
            }
        }
        return files;
    }

    protected static File validatePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        String trimmedPath = rawPath.trim();

        if (trimmedPath.isEmpty()) {
            return null;
        }

        if (containsControlCharacter(trimmedPath)) {
            LOGGER.warn("Rejected path containing control characters: {}",  trimmedPath);
            return null;
        }

        try {
            Path candidate = Paths.get(trimmedPath);

            if (containsParentDirectorySegment(candidate)) {
                LOGGER.warn("Rejected path containing parent-directory navigation: {}",  trimmedPath);
                return null;
            }

            return candidate.toAbsolutePath().normalize().toFile();
        } catch (InvalidPathException _) {
            LOGGER.warn("Rejected invalid path: {}", trimmedPath);
            return null;
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            if (Character.isISOControl(ch)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsParentDirectorySegment(Path path) {
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                return true;
            }
        }

        return false;
    }
}
