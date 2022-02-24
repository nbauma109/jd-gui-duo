/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.sourceloader;

import org.apache.commons.io.IOUtils;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.preferencespanel.MavenOrgSourceLoaderPreferencesProvider;
import org.jd.gui.spi.SourceLoader;
import org.jd.gui.util.TempFile;
import org.jd.util.SHA1Util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

public class MavenOrgSourceLoaderProvider implements SourceLoader {
    protected static final String MAVENORG_SEARCH_URL_PREFIX = "https://search.maven.org/solrsearch/select?q=1:%22";
    protected static final String MAVENORG_SEARCH_URL_SUFFIX = "%22&rows=20&wt=xml";

    protected static final String MAVENORG_LOAD_URL_PREFIX = "https://search.maven.org/classic/remotecontent?filepath=";
    protected static final String MAVENORG_LOAD_URL_SUFFIX = "-sources.jar";

    protected Set<Container.Entry> failed = new HashSet<>();
    protected Map<Container.Entry, File> cache = new HashMap<>();

    @Override
    public String getSource(API api, Container.Entry entry) {
        if (isActivated(api)) {
            String filters = api.getPreferences().get(MavenOrgSourceLoaderPreferencesProvider.FILTERS);

            if (filters == null || filters.isEmpty()) {
                filters = MavenOrgSourceLoaderPreferencesProvider.DEFAULT_FILTERS_VALUE;
            }

            if (accepted(filters, entry.getPath())) {
                return searchSource(entry, cache.get(entry.getContainer().getRoot().getParent()));
            }
        }

        return null;
    }

    @Override
    public String loadSource(API api, Container.Entry entry) {
        if (isActivated(api)) {
            String filters = api.getPreferences().get(MavenOrgSourceLoaderPreferencesProvider.FILTERS);

            if (filters == null || filters.isEmpty()) {
                filters = MavenOrgSourceLoaderPreferencesProvider.DEFAULT_FILTERS_VALUE;
            }

            if (accepted(filters, entry.getPath())) {
                return searchSource(entry, downloadSourceJarFile(entry.getContainer().getRoot().getParent()));
            }
        }

        return null;
    }

    @Override
    public File loadSourceFile(API api, Container.Entry entry) {
        return isActivated(api) ? downloadSourceJarFile(entry) : null;
    }

    private static boolean isActivated(API api) {
        return !"false".equals(api.getPreferences().get(MavenOrgSourceLoaderPreferencesProvider.ACTIVATED));
    }

    protected String searchSource(Container.Entry entry, File sourceJarFile) {
        if (sourceJarFile != null) {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(sourceJarFile)))) {
                ZipEntry ze = zis.getNextEntry();
                String name = entry.getPath();

                name = name.substring(0, name.length()-6) + ".java"; // 6 = ".class".length()

                while (ze != null) {
                    if (ze.getName().equals(name)) {
                        return IOUtils.toString(zis, StandardCharsets.UTF_8);
                    }

                    ze = zis.getNextEntry();
                }

                zis.closeEntry();
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        return null;
    }

    protected File downloadSourceJarFile(Container.Entry entry) {
        if (cache.containsKey(entry)) {
            return cache.get(entry);
        }
        if (!entry.isDirectory() && !failed.contains(entry)) {
            File file = new File(entry.getUri());
            try {
                String sha1 = SHA1Util.computeSHA1(file);
                Artifact artifact = buildArtifactFromURI(file, sha1);
                if (artifact != null && artifact.sourceAvailable()) {
                    // Load source
                    String groupId = artifact.groupId();
                    String artifactId = artifact.artifactId();
                    String version = artifact.version();
                    String filePath = groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + artifactId + '-' + version;
                    URL loadUrl = new URL(MAVENORG_LOAD_URL_PREFIX + filePath + MAVENORG_LOAD_URL_SUFFIX);
                    try (TempFile tmpFile = new TempFile('.' + groupId + '_' + artifactId + '_' + version + MAVENORG_LOAD_URL_SUFFIX);
                        InputStream is = new BufferedInputStream(loadUrl.openStream()); 
                        OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
                        IOUtils.copy(is, os);
                        cache.put(entry, tmpFile);
                        return tmpFile;
                    }
                }
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        failed.add(entry);
        return null;
    }

    public static Artifact buildArtifactFromURI(File file, String sha1) {
        try {
            // Search artifact on maven.org
            URL searchUrl = new URL(MAVENORG_SEARCH_URL_PREFIX + sha1 + MAVENORG_SEARCH_URL_SUFFIX);
            boolean sourceAvailable = false;
            String id = null;
            int numFound = 0;
    
            try (InputStream is = searchUrl.openStream()) {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                XMLStreamReader reader = factory.createXMLStreamReader(is);
                String name = "";
    
                int next;
                while (reader.hasNext()) {
                    next = reader.next();
                    if (next == XMLStreamConstants.START_ELEMENT) {
                        if ("str".equals(reader.getLocalName())) {
                            if ("id".equals(reader.getAttributeValue(null, "name"))) {
                                name = "id";
                            } else {
                                name = "str";
                            }
                        } else if ("result".equals(reader.getLocalName())) {
                            numFound = Integer.parseInt(reader.getAttributeValue(null, "numFound"));
                        } else {
                            name = "";
                        }
                    } else if (next == XMLStreamConstants.CHARACTERS) {
                        if ("id".equals(name)) {
                            id = reader.getText().trim();
                        } else if ("str".equals(name)) {
                            sourceAvailable |= MAVENORG_LOAD_URL_SUFFIX.equals(reader.getText().trim());
                        }
                    }
                }
    
                reader.close();
            }
    
            Artifact artifact = null;
            boolean found = false;
            if (numFound == 0 && file.exists()) {
                // File not indexed by Apache Solr of maven.org -> Try to find groupId, artifactId, version in 'pom.properties'
                Properties pomProperties = getPomProperties(file);
    
                if (pomProperties != null) {
                    String groupId = pomProperties.getProperty("groupId");
                    String artifactId = pomProperties.getProperty("artifactId");
                    String version = pomProperties.getProperty("version");
                    boolean sourceMightBeAvailable = true;
                    artifact = new Artifact(groupId, artifactId, version, file.getName(), found, sourceMightBeAvailable);
                }
            } else if (id != null) {
                int index1 = id.indexOf(':');
                int index2 = id.lastIndexOf(':');
    
                String groupId = id.substring(0, index1);
                String artifactId = id.substring(index1+1, index2);
                String version = id.substring(index2+1);
                found = findPom(groupId, artifactId, version);
                artifact = new Artifact(groupId, artifactId, version, file.getName(), found, sourceAvailable);
            }
            return artifact;
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
            return null;
        }
    }

    private static boolean findPom(String groupId, String artifactId, String version) throws IOException {
        StringBuilder url = new StringBuilder();
        url.append("https://search.maven.org/remotecontent?filepath=");
        url.append(groupId.replace('.', '/'));
        url.append('/');
        url.append(artifactId);
        url.append('/');
        url.append(version);
        url.append('/');
        url.append(artifactId);
        url.append('-');
        url.append(version);
        url.append(".pom");
        HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
        int responseCode = conn.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    private static Properties getPomProperties(File file) {
        // Search 'META-INF/maven/*/*/pom.properties'
        try (JarFile jarFile = new JarFile(file)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry nextEntry = entries.nextElement();
                String entryName = nextEntry.getName();
                if (entryName.startsWith("META-INF/maven/") && entryName.endsWith("/pom.properties")) {
                    try (InputStream is = jarFile.getInputStream(nextEntry)) {
                        Properties properties = new Properties();
                        properties.load(is);
                        return properties;
                    }
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return null;
    }

    protected boolean accepted(String filters, String path) {
        // 'filters' example : '+org +com.google +com.ibm +com.jcraft +com.springsource +com.sun -com +java +javax +sun +sunw'
        StringTokenizer tokenizer = new StringTokenizer(filters);

        String filter;
        while (tokenizer.hasMoreTokens()) {
            filter = tokenizer.nextToken();

            if (filter.length() > 1) {
                String prefix = filter.substring(1).replace('.', '/');

                if (prefix.charAt(prefix.length() - 1) != '/') {
                    prefix += '/';
                }

                if (path.startsWith(prefix)) {
                    return filter.charAt(0) == '+';
                }
            }
        }

        return false;
    }
}
