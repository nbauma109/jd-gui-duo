/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use, 
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv0.gui.service.container;

import java.nio.file.Path;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.GenericContainer;
import org.jd.gui.spi.ContainerFactory;

public class GenericContainerFactoryProvider implements ContainerFactory {

	public String getType() { return "generic"; }

	public boolean accept(API api, Path rootPath) { return true; }

	public Container make(API api, Container.Entry parentEntry, Path rootPath) {
		return new GenericContainer(api, parentEntry, rootPath);
	}
}
