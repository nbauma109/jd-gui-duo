package org.jd.gui.service.fileloader;

import org.jd.gui.spi.FileLoader;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileLoaderServiceMultipartExtensionTest {

    @Test
    void resolvesMultipartArchiveExtensionsToArchiveLoader() {
        FileLoader tarGzLoader = FileLoaderService.getInstance().get(new File("sample.tar.gz"));
        FileLoader tarBz2Loader = FileLoaderService.getInstance().get(new File("sample.tar.bz2"));
        FileLoader tarXzLoader = FileLoaderService.getInstance().get(new File("sample.tar.xz"));
        FileLoader sevenZipLoader = FileLoaderService.getInstance().get(new File("sample.7z"));

        assertNotNull(tarGzLoader);
        assertEquals("org.jd.gui.service.fileloader.ArchiveFileLoaderProvider", tarGzLoader.getClass().getName());
        assertEquals(tarGzLoader.getClass(), tarBz2Loader.getClass());
        assertEquals(tarGzLoader.getClass(), tarXzLoader.getClass());
        assertEquals(tarGzLoader.getClass(), sevenZipLoader.getClass());
    }
}
