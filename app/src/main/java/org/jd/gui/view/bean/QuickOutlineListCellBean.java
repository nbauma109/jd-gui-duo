/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.bean;

import org.jd.gui.api.model.TreeNodeData;

import javax.swing.Icon;

public record QuickOutlineListCellBean(String label, String fragment, Icon icon) implements TreeNodeData {

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getTip() {
        return label;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public Icon getOpenIcon() {
        return icon;
    }
}
