/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.actions;

import org.apache.bcel.classfile.Method;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.cfg.MethodUtil;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

import java.awt.event.ActionEvent;
import java.io.IOException;

import javax.swing.AbstractAction;

public abstract class AbstractMethodAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient API api;
    private final transient Container.Entry entry;
    private final String fragment;

    protected AbstractMethodAction(API api, Container.Entry entry, String fragment) {
    	this.api = api;
        this.entry = entry;
        this.fragment = fragment;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        methodAction(api, entry, fragment);
    }

    private void methodAction(API api, Container.Entry entry, String fragment) {
        Loader loader = new ContainerLoader(entry);
        TypeMaker typeMaker = new TypeMaker(loader);
        if (fragment != null) {
            int dashIndex = fragment.indexOf('-');
            if (dashIndex != -1) {
                int lastDashIndex = fragment.lastIndexOf('-');
                if (dashIndex == lastDashIndex) {
                    // See jd.gui.api.feature.UriOpenable
                    throw new InvalidFormatException("fragment: " + fragment);
                }
                String internalTypeName = fragment.substring(0, dashIndex);
                String methodName = fragment.substring(dashIndex + 1, lastDashIndex);
                String descriptor = fragment.substring(lastDashIndex + 1);
                try {
                    Method method = MethodUtil.searchMethod(loader, typeMaker, internalTypeName, methodName, descriptor);
                    if (method == null) {
                        method = MethodUtil.searchMethod(loader, typeMaker, internalTypeName, "<clinit>", "()V");
                    }
                    methodAction(api, method, internalTypeName);

                } catch (IOException ex) {
                    assert ExceptionUtil.printStackTrace(ex);
                }
            }
        }
    }

    protected abstract void methodAction(API api, Method method, String className);
}
