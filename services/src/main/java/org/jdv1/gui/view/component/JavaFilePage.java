/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.io.TextReader;

public class JavaFilePage extends TypePage {

	private static final long serialVersionUID = 1L;

	public JavaFilePage(API api, Container.Entry entry) {
        super(api, entry);
        // Load content file
        String text = TextReader.getText(entry.getInputStream()).replace("\r\n", "\n").replace('\r', '\n');
        // Parse
        parseAndSetText(text);
    }
    @Override
	public String getSyntaxStyle() { return SyntaxConstants.SYNTAX_STYLE_JAVA; }

    // --- ContentSavable --- //
    @Override
	public String getFileName() {
        String path = entry.getPath();
        int index = path.lastIndexOf('/');
        return path.substring(index+1);
    }
}
