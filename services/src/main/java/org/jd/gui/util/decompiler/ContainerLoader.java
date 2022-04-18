/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

import org.apache.commons.io.IOUtils;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.FileEntryPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

public class ContainerLoader implements Loader {

    private Container.Entry entry;

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
        if (!internalPath.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            return getEntry(internalPath + StringConstants.CLASS_FILE_SUFFIX);
        }
        if (entry.getPath().equals(internalPath)) {
            return entry;
        }
        Map<Container.EntryPath, Container.Entry> children = entry.getParent().getChildren();
        return children.get(new FileEntryPath(internalPath));
    }

    @Override
    public boolean canLoad(String internalPath) {
        return getEntry(internalPath) != null;
    }

    @Override
    public byte[] load(String internalName) throws IOException {
        Container.Entry loadedEntry = getEntry(internalName);
        if (loadedEntry == null) {
            return null;
        }
        try (InputStream inputStream = loadedEntry.getInputStream()) {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public static char[] loadEntry(Container.Entry entry, Charset charset) throws IOException {
        try (InputStream inputStream = entry.getInputStream()) {
            return IOUtils.toCharArray(inputStream, charset);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
}
