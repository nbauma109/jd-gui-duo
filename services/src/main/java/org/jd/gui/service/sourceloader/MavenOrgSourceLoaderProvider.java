/*
 * Copyright (c) 2008-2025 Emmanuel Dupuy and other contributors.
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
import org.jd.gui.util.DownloadUtil;
import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.NexusSearchFactory;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.jd.util.SHA1Util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MavenOrgSourceLoaderProvider implements SourceLoader {
    protected static final String MAVENORG_SEARCH_URL_PREFIX = "https://search.maven.org/solrsearch/select?q=1:%22";
    protected static final String MAVENORG_SEARCH_URL_SUFFIX = "%22&rows=20&wt=xml";

    protected static final String MAVENORG_LOAD_URL_PREFIX = "https://search.maven.org/remotecontent?filepath=";
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
                return searchSource(entry, downloadSourceJarFile(api, entry.getContainer().getRoot().getParent()));
            }
        }

        return null;
    }

    @Override
    public File loadSourceFile(API api, Container.Entry entry) {
        return isActivated(api) ? downloadSourceJarFile(api, entry) : null;
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

    protected File downloadSourceJarFile(API api, Container.Entry entry) {
        if (cache.containsKey(entry)) {
            return cache.get(entry);
        }
        if (!entry.isDirectory() && !failed.contains(entry)) {
            File file = new File(entry.getUri());
            try {
                String sha1 = SHA1Util.computeSHA1(file);
                NexusArtifact artifact = buildArtifactFromURI(api, sha1, true);
                if (artifact != null && artifact.artifactLink() != null) {
                    // Load source
                	URI downloadURI = URI.create(artifact.artifactLink());
					File tmpFile = DownloadUtil.downloadToTemp(downloadURI, api, null);
                    cache.put(entry, tmpFile);
                    return tmpFile;
                }
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        failed.add(entry);
        return null;
    }

    public static NexusArtifact buildArtifactFromURI(API api, String sha1, boolean sources) throws Exception {
    	NexusSearch nexusSearch = NexusSearchFactory.create(api, null);
    	NexusSearchResult nexusSearchResult = nexusSearch.searchBySha1(sha1, 0);
		if (nexusSearchResult.artifacts() != null && !nexusSearchResult.artifacts().isEmpty()) {
			NexusArtifact nexusArtifact = nexusSearchResult.artifacts().get(0);
			if (sources) {
				String g = nexusArtifact.groupId();
				String a = nexusArtifact.artifactId();
				String v = nexusArtifact.version();
				NexusSearchResult results = nexusSearch.searchByGav(g, a, v, "sources", "jar", 0);
				if (results.artifacts() != null && !results.artifacts().isEmpty()) {
					return results.artifacts().get(0);
				}
			}
			return nexusArtifact;
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
