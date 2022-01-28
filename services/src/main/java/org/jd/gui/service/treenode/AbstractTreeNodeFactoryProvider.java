/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.util.ImageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.plaf.IconUIResource;

public abstract class AbstractTreeNodeFactoryProvider implements TreeNodeFactory {

    public static final ImageIcon COLLAPSED_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/plus.png"));
    public static final ImageIcon EXPANDED_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/minus.png"));

    static {
        UIManager.put("Tree.collapsedIcon", new IconUIResource(COLLAPSED_ICON));
        UIManager.put("Tree.expandedIcon", new IconUIResource(EXPANDED_ICON));
    }

    private List<String> externalSelectors;
    protected Pattern externalPathPattern;

    /**
     * Initialize "selectors" and "pathPattern" with optional external properties
     * file
     */
    protected AbstractTreeNodeFactoryProvider() {
        Properties properties = new Properties();
        Class<?> clazz = this.getClass();

        try (InputStream is = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        init(properties);
    }

    protected void init(Properties properties) {
        String selectors = properties.getProperty("selectors");

        if (selectors != null) {
            externalSelectors = Arrays.asList(selectors.split(","));
        }

        String pathRegExp = properties.getProperty("pathRegExp");

        if (pathRegExp != null) {
            externalPathPattern = Pattern.compile(pathRegExp);
        }
    }

    protected String[] appendSelectors(String selector) {
        if (externalSelectors == null) {
            return new String[] { selector };
        }
        int size = externalSelectors.size();
        String[] array = new String[size + 1];
        externalSelectors.toArray(array);
        array[size] = selector;
        return array;
    }

    protected String[] appendSelectors(String... selectors) {
        if (externalSelectors == null) {
            return selectors;
        }
        int size = externalSelectors.size();
        String[] array = new String[size + selectors.length];
        externalSelectors.toArray(array);
        System.arraycopy(selectors, 0, array, size, selectors.length);
        return array;
    }

    @Override
    public Pattern getPathPattern() {
        return externalPathPattern;
    }
}
