/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.BreakpointCapablePage;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.io.TextReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiPredicate;

import jd.core.ClassUtil;

public class JavaFilePage extends TypePage implements BreakpointCapablePage {

    private static final long serialVersionUID = 1L;

    private transient BiPredicate<String, Integer> breakpointToggleHandler;
    private final transient API pageApi;

    public JavaFilePage(API api, Container.Entry entry) {
        super(api, entry);
        this.pageApi = api;
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

    // --- BreakpointCapablePage: the original source is as debuggable as the decompiled class --- //
    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(() -> pageApi.syncBreakpointPage(this));
    }

    @Override
    public String getDebugTypeName() {
        return ClassUtil.getInternalName(entry.getPath()).replace('/', '.');
    }

    @Override
    public void setBreakpointToggleHandler(BiPredicate<String, Integer> handler) {
        this.breakpointToggleHandler = handler;
    }

    @Override
    public void setBreakpointLine(int lineNumber, boolean enabled) {
        setBreakpointLineNumber(lineNumber, enabled);
    }

    @Override
    protected void toggleBreakpointLineNumber(int sourceLineNumber) {
        boolean nowEnabled = !isBreakpointLineNumber(sourceLineNumber);
        setBreakpointLineNumber(sourceLineNumber, nowEnabled);
        if (breakpointToggleHandler != null) {
            boolean vmEnabled = breakpointToggleHandler.test(getDebugTypeName(), sourceLineNumber);
            if (vmEnabled != nowEnabled) {
                setBreakpointLineNumber(sourceLineNumber, vmEnabled);
            }
        }
    }

    // --- ContentSavable --- //
    @Override
    public String getFileName() {
        String path = entry.getPath();
        int index = path.lastIndexOf('/');
        return path.substring(index+1);
    }
}
