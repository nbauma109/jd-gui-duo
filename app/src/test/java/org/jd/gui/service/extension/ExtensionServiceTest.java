/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the extensions are looked for: the jars and the folders of classes below the "ext" directory. */
class ExtensionServiceTest {

    @TempDir
    Path directory;

    private List<URL> search(File from) throws Exception {
        List<URL> found = new ArrayList<>();
        ExtensionService.getInstance().searchJarAndMetaInf(found, from);
        return found;
    }

    @Test
    void theJarsAndTheFoldersThatDeclareServicesAreFound() throws Exception {
        Files.createDirectories(directory.resolve("classes/META-INF"));
        Files.createDirectories(directory.resolve("nested"));
        Files.createFile(directory.resolve("nested/extension.JAR"));
        Files.createFile(directory.resolve("nested/readme.txt"));

        assertThat(search(directory.toFile())).hasSize(2)
                .anySatisfy(url -> assertThat(url.toString()).startsWith("jar:file:").endsWith("extension.JAR!/"))
                .anySatisfy(url -> assertThat(url.toString()).endsWith("/classes/"));
    }

    @Test
    void whatCannotBeListedHasNoExtensions() throws Exception {
        assertThat(search(Files.createFile(directory.resolve("file")).toFile())).isEmpty();
    }
}
