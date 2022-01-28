/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.service.indexer.AbstractIndexerProvider;
import org.jd.gui.util.io.TextReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class TextFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.txt", "*:file:*.html", "*:file:*.xhtml", "*:file:*.js", "*:file:*.jsp", "*:file:*.jspf", "*:file:*.xml", "*:file:*.xsl", "*:file:*.xslt",
                "*:file:*.xsd", "*:file:*.properties", "*:file:*.props", "*:file:*.sql", "*:file:*.yaml", "*:file:*.yml", "*:file:*.json");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        try (InputStream inputStream = entry.getInputStream()) {
            indexes.getIndex("strings").get(TextReader.getText(inputStream)).add(entry);
            updateProgress(entry, getProgressFunction, setProgressFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
