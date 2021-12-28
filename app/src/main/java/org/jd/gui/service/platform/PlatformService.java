/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.platform;

public class PlatformService {
    protected static final PlatformService PLATFORM_SERVICE = new PlatformService();

    public enum OS { LINUX, MAC_OSX, WINDOWS }

    private OS os;

    protected PlatformService() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("windows")) {
            os = OS.WINDOWS;
        } else if (osName.contains("mac os")) {
            os = OS.MAC_OSX;
        } else {
            os = OS.LINUX;
        }
    }

    public static PlatformService getInstance() { return PLATFORM_SERVICE; }

    public OS getOs() { return os; }

    public boolean isLinux() { return os == OS.LINUX; }
    public boolean isMac() { return os == OS.MAC_OSX; }
    public boolean isWindows() { return os == OS.WINDOWS; }
}
