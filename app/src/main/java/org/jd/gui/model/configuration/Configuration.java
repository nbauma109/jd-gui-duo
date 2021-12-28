/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.configuration;

import org.jd.gui.Constants;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration {
    private Point mainWindowLocation;
    private Dimension mainWindowSize;
    private boolean mainWindowMaximize;
    private String lookAndFeel;

    private List<File> recentFiles = new ArrayList<>();

    private File recentLoadDirectory;
    private File recentSaveDirectory;

    private final Map<String, String> preferences = new HashMap<>();

    public Point getMainWindowLocation() {
        return mainWindowLocation;
    }

    public Dimension getMainWindowSize() {
        return mainWindowSize;
    }

    public boolean isMainWindowMaximize() {
        return mainWindowMaximize;
    }

    public String getLookAndFeel() {
        return lookAndFeel;
    }

    public List<File> getRecentFiles() {
        return recentFiles;
    }

    public File getRecentLoadDirectory() {
        return recentLoadDirectory;
    }

    public File getRecentSaveDirectory() {
        return recentSaveDirectory;
    }

    public Map<String, String> getPreferences() {
        return preferences;
    }

    public void setMainWindowLocation(Point mainWindowLocation) {
        this.mainWindowLocation = mainWindowLocation;
    }

    public void setMainWindowSize(Dimension mainWindowSize) {
        this.mainWindowSize = mainWindowSize;
    }

    public void setMainWindowMaximize(boolean mainWindowMaximize) {
        this.mainWindowMaximize = mainWindowMaximize;
    }

    public void setLookAndFeel(String lookAndFeel) {
        this.lookAndFeel = lookAndFeel;
    }

    public void setRecentFiles(List<File> recentFiles) {
        this.recentFiles = recentFiles;
    }

    public void setRecentLoadDirectory(File recentLoadDirectory) {
        this.recentLoadDirectory = recentLoadDirectory;
    }

    public void setRecentSaveDirectory(File recentSaveDirectory) {
        this.recentSaveDirectory = recentSaveDirectory;
    }

    public void addRecentFile(File file) {
        recentFiles.remove(file);
        recentFiles.add(0, file);
        if (recentFiles.size() > Constants.MAX_RECENT_FILES) {
            recentFiles.remove(Constants.MAX_RECENT_FILES);
        }
    }
}
