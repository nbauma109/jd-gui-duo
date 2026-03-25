/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.api.model.ArchiveFormat;

public class ArchiveFileTreeNodeFactoryProvider extends ZipFileTreeNodeFactoryProvider {

    @Override
    public String[] getSelectors() {
        return appendSelectors(ArchiveFormat.selectorsOf(
            ArchiveFormat.TAR_GZ,
            ArchiveFormat.TAR_XZ,
            ArchiveFormat.TAR_BZ2,
            ArchiveFormat.SEVEN_ZIP
        ));
    }
}
