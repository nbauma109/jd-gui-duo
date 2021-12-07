/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.container;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContainerFactory;
import org.jdv1.gui.model.container.KarContainer;

import java.nio.file.*;

@org.kohsuke.MetaInfServices(ContainerFactory.class)
public class KarContainerFactoryProvider implements ContainerFactory {
    @Override
    public String getType() { return "kar"; }

	@Override
	@SuppressWarnings("resource")
    public boolean accept(API api, Path rootPath) {
        if (rootPath.toUri().toString().toLowerCase().endsWith(".kar!/")) {
            return true;
        }
        // Extension: accept uncompressed KAR file containing a folder 'repository'
        try {
        	// do not try to close file system due to UnsupportedOperationException
            return rootPath.getFileSystem().provider().getScheme().equals("file") && Files.exists(rootPath.resolve("repository"));
        } catch (InvalidPathException e) {
            assert ExceptionUtil.printStackTrace(e);
            return false;
        }
    }

    @Override
    public Container make(API api, Container.Entry parentEntry, Path rootPath) {
        return new KarContainer(api, parentEntry, rootPath);
    }
}
