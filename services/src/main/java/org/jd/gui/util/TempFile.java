/*******************************************************************************
 * © 2022-2026 Nicolas Baumann (@nbauma109)
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
package org.jd.gui.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** The name of a file that does not exist yet, in a new temporary directory that only its owner can access. */
public class TempFile extends File implements AutoCloseable {

    private static final long serialVersionUID = 1L;

    public static final String TMP_FILE_PREFIX = "jd-gui.tmp.";

    public TempFile(String suffix) throws IOException {
        super(newDirectory(), TMP_FILE_PREFIX + suffix);
        deleteOnExit();
    }

    private static File newDirectory() throws IOException {
        File directory = Files.createTempDirectory(TMP_FILE_PREFIX).toFile();
        // registered before the file: what is registered last is deleted first
        directory.deleteOnExit();
        return directory;
    }

    @Override
    public void close() {
        deleteOnExit();
    }
}
