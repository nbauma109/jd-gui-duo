/*
 * © 2008-2026 Emmanuel Dupuy
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.util.ImageUtil;

import javax.swing.ImageIcon;

public class ApkFileTreeNodeFactoryProvider extends ZipFileTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/android.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.apk", "*:file:*.dex");
    }

    @Override
    public ImageIcon getIcon() {
        return ICON;
    }
}
