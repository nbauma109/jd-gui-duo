/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.PageCreator;
import org.jd.gui.api.feature.TreeNodeExpandable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.view.data.TreeNodeBean;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

public abstract class AbstractTypeFileTreeNodeFactoryProvider extends AbstractTreeNodeFactoryProvider {

    public static class BaseTreeNode extends DefaultMutableTreeNode implements ContainerEntryGettable, UriGettable, PageCreator {

        private static final long serialVersionUID = 1L;
        protected transient Container.Entry entry;
        protected transient PageAndTipFactory factory;
        private transient URI uri;

        public BaseTreeNode(Container.Entry entry, String fragment, Object userObject, PageAndTipFactory factory) {
            super(userObject);
            this.entry = entry;
            this.factory = factory;

            if (fragment != null) {
                try {
                    URI localURI = entry.getUri();
                    this.uri = new URI(localURI.getScheme(), localURI.getHost(), localURI.getPath(), fragment);
                } catch (URISyntaxException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else {
                this.uri = entry.getUri();
            }
        }

        // --- ContainerEntryGettable --- //
        @Override
        public Container.Entry getEntry() {
            return entry;
        }

        // --- UriGettable --- //
        @Override
        public URI getUri() {
            return uri;
        }

        // --- PageCreator --- //
        @Override
        public <T extends JComponent & UriGettable> T createPage(API api) {
            // Lazy 'tip' initialization
            ((TreeNodeBean) userObject).setTip(factory.makeTip(api, entry));
            return factory.makePage(api, entry);
        }
    }

    protected static class FileTreeNode extends BaseTreeNode implements TreeNodeExpandable {

        private static final long serialVersionUID = 1L;
        protected boolean initialized;

        public FileTreeNode(Container.Entry entry, Object userObject, PageAndTipFactory pageAndTipFactory) {
            this(entry, null, userObject, pageAndTipFactory);
        }

        public FileTreeNode(Container.Entry entry, String fragment, Object userObject, PageAndTipFactory factory) {
            super(entry, fragment, userObject, factory);
            initialized = false;
            // Add dummy node
            add(new DefaultMutableTreeNode());
        }

        // --- TreeNodeExpandable --- //
        @Override
        public void populateTreeNode(API api) {
            if (!initialized) {
                removeAllChildren();
                // Create type node
                TypeFactory typeFactory = api.getTypeFactory(entry);

                if (typeFactory != null) {
                    Collection<Type> types = typeFactory.make(api, entry);

                    for (Type type : types) {
                        add(new TypeTreeNode(entry, type, new TreeNodeBean(type.getDisplayTypeName(), type.getIcon()), factory));
                    }
                }

                initialized = true;
            }
        }
    }

    protected static class TypeTreeNode extends BaseTreeNode implements TreeNodeExpandable {

        private static final long serialVersionUID = 1L;
        private boolean initialized;
        private transient Type type;

        public TypeTreeNode(Container.Entry entry, Type type, Object userObject, PageAndTipFactory factory) {
            super(entry, type.getName(), userObject, factory);
            this.initialized = false;
            this.type = type;
            // Add dummy node
            add(new DefaultMutableTreeNode());
        }

        // --- TreeNodeExpandable --- //
        @Override
        public void populateTreeNode(API api) {
            if (!initialized) {
                removeAllChildren();

                String typeName = type.getName();

                // Create inner types
                Collection<Type> innerTypes = type.getInnerTypes();

                if (innerTypes != null) {
                    List<Type> innerTypeList = new ArrayList<>(innerTypes);
                    innerTypeList.sort(Comparator.comparing(Type::getName));

                    for (Type innerType : innerTypeList) {
                        add(new TypeTreeNode(entry, innerType, new TreeNodeBean(innerType.getDisplayInnerTypeName(), innerType.getIcon()), factory));
                    }
                }

                // Create fields
                Collection<Type.Field> fields = type.getFields();

                if (fields != null) {
                    List<FieldOrMethodBean> beans = new ArrayList<>(fields.size());

                    for (Type.Field field : fields) {
                        String fragment = typeName + '-' + field.getName() + '-' + field.getDescriptor();
                        beans.add(new FieldOrMethodBean(fragment, field.getDisplayName(), field.getIcon()));
                    }

                    beans.sort(Comparator.comparing(FieldOrMethodBean::getLabel));

                    for (FieldOrMethodBean bean : beans) {
                        add(new FieldOrMethodTreeNode(entry, bean.fragment, new TreeNodeBean(bean.label, bean.icon), factory));
                    }
                }

                // Create methods
                Collection<Type.Method> methods = type.getMethods();

                if (methods != null) {
                    List<FieldOrMethodBean> beans = new ArrayList<>();

                    for (Type.Method method : methods) {
                        if (!"<clinit>".equals(method.getName())) {
                            String fragment = typeName + '-' + method.getName() + '-' + method.getDescriptor();
                            beans.add(new FieldOrMethodBean(fragment, method.getDisplayName(), method.getIcon()));
                        }
                    }

                    beans.sort(Comparator.comparing(FieldOrMethodBean::getLabel));

                    for (FieldOrMethodBean bean : beans) {
                        add(new FieldOrMethodTreeNode(entry, bean.fragment, new TreeNodeBean(bean.label, bean.icon), factory));
                    }
                }

                initialized = true;
            }
        }
    }

    protected static class FieldOrMethodTreeNode extends BaseTreeNode {

        private static final long serialVersionUID = 1L;

        public FieldOrMethodTreeNode(Container.Entry entry, String fragment, Object userObject, PageAndTipFactory factory) {
            super(entry, fragment, userObject, factory);
        }
    }

    protected static class FieldOrMethodBean {
        private final String fragment;
        private final String label;
        private final Icon icon;

        public FieldOrMethodBean(String fragment, String label, Icon icon) {
            this.fragment = fragment;
            this.label = label;
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }
    }

    protected interface PageAndTipFactory {
        <T extends JComponent & UriGettable> T makePage(API api, Container.Entry entry);

        String makeTip(API api, Container.Entry entry);
    }

}
