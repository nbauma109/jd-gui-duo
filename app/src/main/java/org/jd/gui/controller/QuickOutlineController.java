/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.FocusedTypeGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.view.QuickOutlineView;
import org.jd.gui.view.bean.QuickOutlineListCellBean;

import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.tree.DefaultMutableTreeNode;

public class QuickOutlineController {

    private final API api;
    private final QuickOutlineView quickOutlineView;

    private URI currentUri;
    private UriOpenable currentUriOpenable;

    public QuickOutlineController(API api, JFrame mainFrame) {
        this.api = api;
        this.quickOutlineView = new QuickOutlineView(mainFrame, this::onMemberSelected);
    }

    public void show(
            FocusedTypeGettable focusedTypeGettable,
            UriGettable uriGettable,
            UriOpenable uriOpenable,
            JComponent anchorComponent) {
        Container.Entry entry = focusedTypeGettable.getEntry();
        String focusedTypeName = focusedTypeGettable.getFocusedTypeName();

        if (focusedTypeName == null) {
            focusedTypeName = inferFocusedTypeName(entry);
        }
        if (focusedTypeName == null) {
            return;
        }

        Type type = api.getTypeFactory(entry).make(api, entry, focusedTypeName);
        if (type == null) {
            return;
        }

        this.currentUri = uriGettable.getUri();
        this.currentUriOpenable = uriOpenable;

        quickOutlineView.show(createTree(type), anchorComponent);
    }

    protected DefaultMutableTreeNode createTree(Type type) {
        String typeName = type.getName();
        String typeDisplayName = type.getDisplayInnerTypeName() != null ? type.getDisplayInnerTypeName() : type.getDisplayTypeName();
        if (typeDisplayName == null) {
            typeDisplayName = typeName;
        }

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                new QuickOutlineListCellBean(typeDisplayName, typeName, type.getIcon()));

        for (Type.Field field : type.getFields()) {
            String name = field.getName();
            String descriptor = field.getDescriptor();

            if (name != null && descriptor != null) {
                String label = field.getDisplayName() == null ? name : field.getDisplayName();
                root.add(new DefaultMutableTreeNode(
                        new QuickOutlineListCellBean(label, key(typeName, name, descriptor), field.getIcon())));
            }
        }

        for (Type.Method method : type.getMethods()) {
            String name = method.getName();
            String descriptor = method.getDescriptor();

            if (name != null && descriptor != null) {
                String label = method.getDisplayName() == null ? name : method.getDisplayName();
                root.add(new DefaultMutableTreeNode(
                        new QuickOutlineListCellBean(label, key(typeName, name, descriptor), method.getIcon())));
            }
        }

        return root;
    }

    protected void onMemberSelected(String fragment) {
        if (currentUri == null || currentUriOpenable == null || fragment == null || fragment.isEmpty()) {
            return;
        }

        try {
            URI uri = new URI(currentUri.getScheme(), currentUri.getAuthority(), currentUri.getPath(), null, fragment);
            currentUriOpenable.openUri(uri);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    protected static String inferFocusedTypeName(Container.Entry entry) {
        if (entry == null) {
            return null;
        }

        String path = entry.getPath();
        if (path == null) {
            return null;
        }

        if (path.endsWith(".class")) {
            return path.substring(0, path.length() - 6);
        }
        if (path.endsWith(".java")) {
            return path.substring(0, path.length() - 5);
        }
        return null;
    }

    protected static String key(CharSequence internalName, CharSequence name, CharSequence descriptor) {
        return String.join("-", internalName, name, descriptor);
    }
}
