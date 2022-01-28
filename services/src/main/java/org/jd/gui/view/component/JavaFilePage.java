/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.io.TextReader;

import java.io.IOException;
import java.io.InputStream;

public class JavaFilePage extends TypePage {

    private static final long serialVersionUID = 1L;

    public JavaFilePage(API api, Container.Entry entry) {
        super(api, entry);
        try (InputStream inputStream = entry.getInputStream()) {
            // Load content file
            String text = TextReader.getText(inputStream).replace("\r\n", "\n").replace('\r', '\n');
            // Parse
            parseAndSetText(text);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
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
