/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.service.uriloader;

import org.apache.commons.lang3.ArrayUtils;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.spi.FileLoader;
import org.jd.gui.spi.UriLoader;
import org.jd.gui.util.DownloadUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class HttpUriLoaderProvider implements UriLoader {
    protected static final String[] SCHEMES = { "http", "https" };

    @Override
    public String[] getSchemes() { return SCHEMES; }

    @Override
    public boolean accept(API api, URI uri) {
    	return ArrayUtils.contains(SCHEMES, uri.getScheme());
    }

    @Override
	public boolean load(API api, URI uri) {
		try {
			File tmpFile = DownloadUtil.downloadToTemp(uri, api, null);
			FileLoader fileLoader = api.getFileLoader(tmpFile);
			return fileLoader != null && fileLoader.load(api, tmpFile);
		} catch (IOException e) {
			assert ExceptionUtil.printStackTrace(e);
		}
		return false;
	}

}
