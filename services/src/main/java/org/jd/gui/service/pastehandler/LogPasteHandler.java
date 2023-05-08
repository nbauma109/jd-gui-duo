/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.pastehandler;

import org.jd.gui.api.API;
import org.jd.gui.spi.PasteHandler;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.view.component.LogPage;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ImageIcon;

public class LogPasteHandler implements PasteHandler { // NO_UCD (unused code)
    protected static final AtomicInteger counter = new AtomicInteger(0);
    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/file_obj.png"));

    @Override
    public boolean accept(Object obj) { return obj instanceof String; }

    @Override
    public void paste(API api, Object obj) {
        String title = "clipboard-" + counter.incrementAndGet() + ".log";
        URI uri = URI.create("memory://" + title);
        String content = obj == null ? "" : obj.toString();
        api.addPanel(null, title, () -> ICON, null, new LogPage(api, uri, content));
    }
}
