/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.data;

import org.jd.gui.api.model.TreeNodeData;

import javax.swing.Icon;

public class TreeNodeBean implements TreeNodeData {
    protected final String label;
    protected String tip;
    protected final Icon icon;
    protected Icon openIcon;

    public TreeNodeBean(String label, Icon icon) {
        this.label = label;
        this.icon = icon;
    }

    public TreeNodeBean(String label, String tip, Icon icon) {
        this.label = label;
        this.tip = tip;
        this.icon = icon;
    }

    public TreeNodeBean(String label, Icon icon, Icon openIcon) {
        this.label = label;
        this.icon = icon;
        this.openIcon = openIcon;
    }

    public TreeNodeBean(String label, String tip, Icon icon, Icon openIcon) {
        this.label = label;
        this.tip = tip;
        this.icon = icon;
        this.openIcon = openIcon;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    @Override
    public String getLabel() {

        return label;
    }

    @Override
    public String getTip() {
        return tip;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public Icon getOpenIcon() {
        return openIcon;
    }
}
