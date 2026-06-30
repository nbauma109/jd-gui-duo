/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.service.container.JarContainerFactoryProvider;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JarFileLoaderProviderTest {
    @Test
    void loadAspectjweaver1813() throws Exception {
        TestJarFileLoaderProvider loader = new TestJarFileLoaderProvider();
        File jar = Path.of("target/test-jars/aspectjweaver-1.8.13.jar").toFile();

        assertTrue(jar.isFile());
        assertTrue(loader.load(null, jar));
        assertTrue(loader.manifestFound);
        assertTrue(loader.jarContainerFound);
    }

    private static class TestJarFileLoaderProvider extends JarFileLoaderProvider {
        private boolean manifestFound;
        private boolean jarContainerFound;

        @Override
        @SuppressWarnings("unchecked")
        protected <T extends JComponent & UriGettable> T load(API api, File file, Path rootPath) {
            manifestFound = Files.exists(rootPath.resolve("META-INF/MANIFEST.MF"));
            jarContainerFound = new JarContainerFactoryProvider().accept(api, rootPath);
            return (T)new TestPanel();
        }
    }

    private static class TestPanel extends JPanel implements UriGettable {
        @Override
        public URI getUri() {
            return null;
        }
    }
}
