/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.actions;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.cfg.MethodUtil;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

import java.awt.event.ActionEvent;
import java.io.IOException;

import javax.swing.AbstractAction;

public abstract class AbstractMethodAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient Container.Entry entry;
    private final String fragment;

    protected AbstractMethodAction(Container.Entry entry, String fragment) {
        this.entry = entry;
        this.fragment = fragment;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        methodAction(entry, fragment);
    }

    private void methodAction(Container.Entry entry, String fragment) {
        Loader loader = new ContainerLoader(entry);
        TypeMaker typeMaker = new TypeMaker(loader);
        String entryPath = entry.getPath();
        String internalTypeName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()
        if (fragment != null) {
            int dashIndex = fragment.indexOf('-');
            if (dashIndex != -1) {
                int lastDashIndex = fragment.lastIndexOf('-');
                if (dashIndex == lastDashIndex) {
                    // See jd.gui.api.feature.UriOpenable
                    throw new InvalidFormatException("fragment: " + fragment);
                }
                String methodName = fragment.substring(dashIndex + 1, lastDashIndex);
                String descriptor = fragment.substring(lastDashIndex + 1);
                try {
                    Method method = MethodUtil.searchMethod(loader, typeMaker, internalTypeName, methodName, descriptor);
                    methodAction(method);
                    
                } catch (IOException ex) {
                    assert ExceptionUtil.printStackTrace(ex);
                }
            }
        }
    }

    protected abstract void methodAction(Method method);
}
