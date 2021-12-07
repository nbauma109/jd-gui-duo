/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.io.TextReader;
import org.jd.gui.util.xml.AbstractXmlPathFinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@org.kohsuke.MetaInfServices(org.jd.gui.spi.Indexer.class)
public class WebXmlFileIndexerProvider extends XmlBasedFileIndexerProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:WEB-INF/web.xml"); }

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes) {
        super.index(api, entry, indexes);

        try (InputStream inputStream = entry.getInputStream()) {
            new WebXmlPathFinder(entry, indexes).find(TextReader.getText(inputStream));
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    protected static class WebXmlPathFinder extends AbstractXmlPathFinder {
        Container.Entry entry;
        @SuppressWarnings("all")
        Map<String, Collection> index;

        public WebXmlPathFinder(Container.Entry entry, Indexes indexes) {
            super(Arrays.asList(
                "web-app/filter/filter-class",
                "web-app/listener/listener-class",
                "web-app/servlet/servlet-class"
            ));
            this.entry = entry;
            this.index = indexes.getIndex("typeReferences");
        }

		@Override
		@SuppressWarnings("unchecked")
        public void handle(String path, String text, int position) {
            index.get(text.replace('.', '/')).add(entry);
        }
    }
}
