/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.feature;

public interface ContentSearchable {
    boolean highlightText(String text, SearchType searchType);

    void findNext(String text, SearchType searchType);

    void findPrevious(String text, SearchType searchType);

    record SearchType(boolean caseSensitive, boolean wholeWord, boolean regex) {
    }
}
