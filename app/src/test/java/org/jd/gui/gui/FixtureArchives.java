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

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger();

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

    /**
     * A variant of the fixture jar to compare with it: Greeter is changed, Shape is the same, Main is gone and a text file is added.
     */
    static Path createVariantJar(Path dir, Path fixtureClasses) throws IOException {
        Path work = Files.createDirectories(dir.resolve("variant-classes"));
        Path source = work.resolve("demo/Greeter.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package demo; public class Greeter implements Shape { public String greet() { return \"changed\"; } public double area() { return 1.0; } }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler.run(null, null, null, "-cp", fixtureClasses.toString(), "-d", work.toString(), source.toString()) != 0) {
            throw new IllegalStateException("Could not compile the variant of Greeter");
        }
        Path jar = dir.resolve("variant.jar");
        try (OutputStream out = Files.newOutputStream(jar); java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("demo/Greeter.class"));
            Files.copy(work.resolve("demo/Greeter.class"), zip);
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("demo/Shape.class"));
            Files.copy(fixtureClasses.resolve("demo/Shape.class"), zip);
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("added.txt"));
            zip.write("new file".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
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

    /** One entry of every kind of text, source and image file the application has a page for, and two nested archives. */
    static Path createAssorted(Path dir, Path nestedJar) throws IOException {
        Path zip = dir.resolve("assorted-" + COUNTER.incrementAndGet() + ".zip"); // a path the application has not seen yet
        java.util.Map<String, String> texts = new java.util.LinkedHashMap<>();
        texts.put("readme.txt", "Plain text of the readme\nsecond line");
        texts.put("notes.md", "# Notes\nSome markdown");
        texts.put("config.yml", "server:\n  port: 8080\n");
        texts.put("data.properties", "greeting=Hello\nanswer=42\n");
        texts.put("app.cfg", "mode=fixture\n");
        texts.put("style.css", "body { color: #336699; }\n");
        texts.put("page.html", "<html><body><h1>Fixture page</h1></body></html>\n");
        texts.put("doc.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>Fixture xhtml</body></html>\n");
        texts.put("script.js", "function fixture() { return 'js'; }\n");
        texts.put("data.json", "{\"fixture\": [1, 2, 3]}\n");
        texts.put("query.sql", "SELECT fixture FROM table_one;\n");
        texts.put("run.bat", "@echo fixture batch\n");
        texts.put("build.gradle", "plugins { id 'java' }\n");
        texts.put("Hello.groovy", "class HelloGroovy { String greet() { 'hi' } }\n");
        texts.put("Hello.kt", "class HelloKotlin { fun greet() = \"hi\" }\n");
        texts.put("Hello.scala", "class HelloScala { def greet = \"hi\" }\n");
        texts.put("view.jsp", "<%@ page language=\"java\" %><p>Fixture jsp</p>\n");
        texts.put("schema.xsd", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"fixtureElement\" type=\"xs:string\"/></xs:schema>\n");
        texts.put("transform.xsl", "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"/\">fixtureXsl</xsl:template></xsl:stylesheet>\n");
        texts.put("service.wsdl", "<definitions name=\"FixtureService\" xmlns=\"http://schemas.xmlsoap.org/wsdl/\"/>\n");
        texts.put("tags.tld", "<taglib><tlib-version>1.0</tlib-version><short-name>fixtureTags</short-name></taglib>\n");
        texts.put("config.xml", "<?xml version=\"1.0\"?>\n<config><entry key=\"fixture\">value</entry></config>\n");
        texts.put("doctype.dtd", "<!ELEMENT fixture (#PCDATA)>\n");
        texts.put("Hello.java", "package demo;\n\npublic class Hello {\n    public String greet() {\n        return \"hello from source\";\n    }\n}\n");
        try (OutputStream out = Files.newOutputStream(zip); java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            for (java.util.Map.Entry<String, String> entry : texts.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.putNextEntry(new java.util.zip.ZipEntry("icon.png"));
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(image, "png", zos);
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("lib.jar"));
            Files.copy(nestedJar, zos);
            zos.closeEntry();
        }
        return zip;
    }

    /** The text entries of {@link #createAssorted}: name and a fragment that its page must show. */
    static java.util.Map<String, String> assortedFragments() {
        java.util.Map<String, String> fragments = new java.util.LinkedHashMap<>();
        fragments.put("readme.txt", "Plain text of the readme");
        fragments.put("notes.md", "Some markdown");
        fragments.put("config.yml", "port: 8080");
        fragments.put("data.properties", "answer=42");
        fragments.put("app.cfg", "mode=fixture");
        fragments.put("style.css", "#336699");
        fragments.put("page.html", "Fixture page");
        fragments.put("doc.xhtml", "Fixture xhtml");
        fragments.put("script.js", "function fixture()");
        fragments.put("data.json", "\"fixture\"");
        fragments.put("query.sql", "SELECT fixture");
        fragments.put("run.bat", "fixture batch");
        fragments.put("build.gradle", "plugins");
        fragments.put("Hello.groovy", "HelloGroovy");
        fragments.put("Hello.kt", "HelloKotlin");
        fragments.put("Hello.scala", "HelloScala");
        fragments.put("view.jsp", "Fixture jsp");
        fragments.put("schema.xsd", "fixtureElement");
        fragments.put("transform.xsl", "fixtureXsl");
        fragments.put("service.wsdl", "FixtureService");
        fragments.put("tags.tld", "fixtureTags");
        fragments.put("config.xml", "<entry key=\"fixture\">");
        fragments.put("doctype.dtd", "ELEMENT fixture");
        fragments.put("Hello.java", "hello from source");
        return fragments;
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
