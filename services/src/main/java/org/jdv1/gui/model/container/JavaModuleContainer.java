/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.model.container;

import java.nio.file.Path;

import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.GenericContainer;
import org.jd.gui.api.API;

public class JavaModuleContainer extends GenericContainer {
    public JavaModuleContainer(API api, Container.Entry parentEntry, Path rootPath) {
        super(api, parentEntry, rootPath);
    }

    public String getType() { return "jmod"; }
}
