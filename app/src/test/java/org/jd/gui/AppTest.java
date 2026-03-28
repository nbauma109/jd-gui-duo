/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @Test
    void checkVersionFlag_returnsTrueForLongVersionFlag() {
        assertTrue(App.checkVersionFlag(new String[]{"--version"}));
    }

    @Test
    void checkVersionFlag_returnsTrueForShortVersionFlag() {
        assertTrue(App.checkVersionFlag(new String[]{"-v"}));
    }

    @Test
    void checkVersionFlag_returnsFalseForOtherArgs() {
        assertFalse(App.checkVersionFlag(new String[]{"file.jar"}));
    }

    @Test
    void checkVersionFlag_returnsFalseForNull() {
        assertFalse(App.checkVersionFlag(null));
    }

    @Test
    void checkVersionFlag_returnsTrueWhenMixedWithFiles() {
        assertTrue(App.checkVersionFlag(new String[]{"file.jar", "--version"}));
    }

    @Test
    void checkHelpFlag_returnsTrueForHelpFlag() {
        assertTrue(App.checkHelpFlag(new String[]{"-h"}));
    }

    @Test
    void checkHelpFlag_returnsFalseForNull() {
        assertFalse(App.checkHelpFlag(null));
    }

    @Test
    void getVersion_returnsNonNull() {
        assertNotNull(App.getVersion());
    }

    @Test
    void newList_skipsFlagArguments() {
        List<File> files = App.newList(new String[]{"--version", "-v", "-h"});
        assertTrue(files.isEmpty());
    }

    @Test
    void newList_skipsVersionFlagButKeepsFilePaths() {
        List<File> files = App.newList(new String[]{"--version", "some/valid/path.jar"});
        assertFalse(files.isEmpty());
    }

    @Test
    void newList_returnsEmptyListForNull() {
        assertTrue(App.newList(null).isEmpty());
    }
}
