/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.pastehandler;

import org.jd.gui.api.API;
import org.jd.gui.spi.PasteHandler;
import org.jd.gui.view.component.LogPage;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

public class LogPasteHandler implements PasteHandler {
    protected static final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public boolean accept(Object obj) { return obj instanceof String; }

    @Override
    public void paste(API api, Object obj) {
        String title = "clipboard-" + counter.incrementAndGet() + ".log";
        URI uri = URI.create("memory://" + title);
        String content = obj == null ? "" : obj.toString();
        api.addPanel(title, null, null, new LogPage(api, uri, content));
    }
}
