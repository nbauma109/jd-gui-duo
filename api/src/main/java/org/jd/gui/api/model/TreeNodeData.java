/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.model;

import javax.swing.Icon;

public interface TreeNodeData {
    String getLabel();

    String getTip();

    Icon getIcon();

    Icon getOpenIcon();
}
