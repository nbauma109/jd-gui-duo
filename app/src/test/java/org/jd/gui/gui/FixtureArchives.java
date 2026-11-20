/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Archives with the special entries (manifest, module descriptor, web.xml) that have a page of their own. */
final class FixtureArchives {

    private static final String WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="4.0">
              <display-name>Fixture application</display-name>
              <description>Used by the GUI tests</description>
              <context-param>
                <param-name>environment</param-name>
                <param-value>test</param-value>
              </context-param>
              <filter>
                <filter-name>auditFilter</filter-name>
                <filter-class>demo.AuditFilter</filter-class>
              </filter>
              <filter-mapping>
                <filter-name>auditFilter</filter-name>
                <url-pattern>/*</url-pattern>
              </filter-mapping>
              <listener>
                <listener-class>demo.StartupListener</listener-class>
              </listener>
              <servlet>
                <servlet-name>hello</servlet-name>
                <servlet-class>demo.HelloServlet</servlet-class>
                <load-on-startup>1</load-on-startup>
              </servlet>
              <servlet-mapping>
                <servlet-name>hello</servlet-name>
                <url-pattern>/hello</url-pattern>
              </servlet-mapping>
              <session-config>
                <session-timeout>30</session-timeout>
              </session-config>
              <welcome-file-list>
                <welcome-file>index.html</welcome-file>
              </welcome-file-list>
            </web-app>
            """;

    private FixtureArchives() {
    }

    /** A modular jar: a module descriptor, a class and a manifest with a main section and a named one. */
    static Path createModularJar(Path dir) throws IOException {
        Path classes = Files.createDirectories(dir.resolve("modular-classes"));
        compile(classes, List.of(
                source("module-info", "module fixture.mod { requires java.logging; requires transitive java.sql; exports demo.api; opens demo.impl to java.logging; uses demo.api.Service; provides demo.api.Service with demo.impl.ServiceImpl; }"),
                source("demo/api/Service", "package demo.api; public interface Service { String name(); }"),
                source("demo/impl/ServiceImpl", "package demo.impl; public class ServiceImpl implements demo.api.Service { public String name() { return \"impl\"; } }")));

        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.put(Attributes.Name.MAIN_CLASS, "demo.impl.ServiceImpl");
        main.put(Attributes.Name.CLASS_PATH, "lib/one.jar lib/two.jar");
        main.put(Attributes.Name.IMPLEMENTATION_TITLE, "Fixture module");
        main.putValue("Automatic-Module-Name", "fixture.mod");
        Attributes section = new Attributes();
        section.putValue("Sealed", "true");
        manifest.getEntries().put("demo/api/", section);

        Path jar = dir.resolve("modular.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out, manifest)) {
            addTree(jos, classes);
        }
        return jar;
    }

    /** A web archive with a deployment descriptor and a manifest. */
    static Path createWar(Path dir) throws IOException {
        Path war = dir.resolve("webapp.war");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "Fixture web application");
        try (OutputStream out = Files.newOutputStream(war); JarOutputStream jos = new JarOutputStream(out, manifest)) {
            jos.putNextEntry(new JarEntry("WEB-INF/web.xml"));
            jos.write(WEB_XML.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("index.html"));
            jos.write("<html><body>fixture</body></html>".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return war;
    }

    private static void addTree(JarOutputStream jos, Path root) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                jos.putNextEntry(new JarEntry(root.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
    }

    private static void compile(Path classes, List<SimpleJavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || !compiler.getTask(null, null, null, List.of("-d", classes.toString()), null, sources).call()) {
            throw new IllegalStateException("Could not compile the module fixtures");
        }
    }

    private static SimpleJavaFileObject source(String path, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + path + ".java"), SimpleJavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }
}
