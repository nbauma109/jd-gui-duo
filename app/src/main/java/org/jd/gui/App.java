/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.controller.MainController;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.service.configuration.ConfigurationPersister;
import org.jd.gui.service.configuration.ConfigurationPersisterService;
import org.jd.gui.util.net.InterProcessCommunicationUtil;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

public class App {
    protected static final String SINGLE_INSTANCE = "UIMainWindowPreferencesProvider.singleInstance";

    protected static MainController controller;

    public static void main(String[] args) {
        if (checkHelpFlag(args)) {
            JOptionPane.showMessageDialog(null, "Usage: jd-gui [option] [input-file] ...\n\nOption:\n -h Show this help message and exit", Constants.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
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

    protected static boolean checkHelpFlag(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("-h".equals(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static List<File> newList(String[] paths) {
        if (paths == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>(paths.length);
        for (String rawPath : paths) {
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
            System.err.println("Rejected path containing control characters: " + trimmedPath);
            return null;
        }

        try {
            Path candidate = Paths.get(trimmedPath);

            if (containsParentDirectorySegment(candidate)) {
                System.err.println("Rejected path containing parent-directory navigation: " + trimmedPath);
                return null;
            }

            return candidate.toAbsolutePath().normalize().toFile();
        } catch (InvalidPathException e) {
            System.err.println("Rejected invalid path: " + trimmedPath);
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
