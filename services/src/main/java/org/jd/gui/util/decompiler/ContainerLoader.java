/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.IOUtils;

public class ContainerLoader implements InvocationHandler, Loader {

	protected Container.Entry entry;

	public ContainerLoader() {
		this.entry = null;
	}

	public ContainerLoader(Container.Entry entry) {
		this.entry = entry;
	}

	public void setEntry(Container.Entry e) {
		this.entry = e;
	}

	protected Container.Entry getEntry(String internalPath) {
		String path = internalPath + ".class";
		if (entry.getPath().equals(path) || entry.getPath().equals(internalPath)) {
			return entry;
		}
		for (Container.Entry e : entry.getParent().getChildren()) {
			if (e.getPath().equals(path) || e.getPath().equals(internalPath)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "canLoad":
			return canLoad((String) args[0]);
		case "load":
			return new DataInputStream(getEntry((String) args[0]).getInputStream());
		}
		throw new IllegalArgumentException("Unknown method call");
	}

	@Override
	public boolean canLoad(String internalPath) {
		return getEntry(internalPath) != null;
	}

	@Override
	public byte[] load(String internalName) throws LoaderException {
		Container.Entry entry = getEntry(internalName);
		try {
			return IOUtils.toByteArray(entry.getInputStream());
		} catch (IOException e) {
			throw new LoaderException(e);
		}
	}

	public static char[] loadEntry(Container.Entry entry, Charset charset) throws LoaderException {
		try {
			return IOUtils.toCharArray(entry.getInputStream());
		} catch (IOException e) {
			throw new LoaderException(e);
		}
	}
}
