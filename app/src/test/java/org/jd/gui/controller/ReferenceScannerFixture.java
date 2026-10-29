/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import java.util.List;

/** Compiled class scanned by {@link ReferenceScannerTest}; the member names are the assertions' subjects. */
@SuppressWarnings("all")
class ReferenceScannerFixture {
    String name;
    List<String> items;

    void callsMethod() {
        String.valueOf(1);
    }

    void readsField() {
        System.out.println(name);
    }

    void usesString() {
        name = "needle-string";
    }

    void buildsType() {
        new StringBuilder().append('x');
    }

    void throwsType() throws java.io.IOException {
    }

    void untouched() {
    }
}
