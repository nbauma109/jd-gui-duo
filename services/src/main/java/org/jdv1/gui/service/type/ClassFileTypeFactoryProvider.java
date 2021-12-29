/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.type;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.service.type.AbstractTypeFactoryProvider;
import org.jd.util.LRUCache;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;

public class ClassFileTypeFactoryProvider extends AbstractTypeFactoryProvider {

    // Create cache
    protected LRUCache<URI, JavaType> cache = new LRUCache<>();

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.class"); }

    @Override
    public Collection<Type> make(API api, Container.Entry entry) {
        return Collections.singletonList(make(api, entry, null));
    }

    @Override
    public Type make(API api, Container.Entry entry, String fragment) {
        URI key = entry.getUri();

        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        JavaType type;

        try (InputStream is = entry.getInputStream()) {
            ClassReader classReader = new ClassReader(is);

            if (fragment != null && !fragment.isEmpty()) {
                // Search type name in fragment. URI format : see jd.gui.api.feature.UriOpener
                int index = fragment.indexOf('-');
                if (index != -1) {
                    // Keep type name only
                    fragment = fragment.substring(0, index);
                }

                if (!classReader.getClassName().equals(fragment)) {
                    // Search entry for type name
                    String entryTypePath = classReader.getClassName() + StringConstants.CLASS_FILE_SUFFIX;
                    String fragmentTypePath = fragment + StringConstants.CLASS_FILE_SUFFIX;

                    while (true) {
                        if (entry.getPath().endsWith(entryTypePath)) {
                            // Entry path ends with the internal class name
                            String pathToFind = entry.getPath().substring(0, entry.getPath().length() - entryTypePath.length()) + fragmentTypePath;
                            Container.Entry entryFound = entry.getParent().getChildren().get(new FileEntryPath(pathToFind));

                            if (entryFound == null) {
								return null;
							}

                            entry = entryFound;

                            try (@SuppressWarnings("all") InputStream is2 = entry.getInputStream()) {
                                classReader = new ClassReader(is2);
                            } catch (IOException e) {
                                assert ExceptionUtil.printStackTrace(e);
                                return null;
                            }
                            break;
                        }

                        // Truncated path ? Cut first package name and retry
                        int firstPackageSeparatorIndex = entryTypePath.indexOf('/');
                        if (firstPackageSeparatorIndex == -1) {
                            // Nothing to cut -> Stop
                            return null;
                        }

                        entryTypePath = entryTypePath.substring(firstPackageSeparatorIndex + 1);
                        fragmentTypePath = fragmentTypePath.substring(fragmentTypePath.indexOf('/') + 1);
                    }
                }
            }

            type = new JavaType(entry, classReader, -1);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
            type = null;
        }

        cache.put(key, type);
        return type;
    }

    static class JavaType implements Type {
        private final Container.Entry entry;
        private int access;
        private String name;
        private String superName;
        private String outerName;

        private String displayTypeName;
        private String displayInnerTypeName;
        private final String displayPackageName;

        private List<Type> innerTypes;
        private final List<Type.Field> fields = new ArrayList<>();
        private final List<Type.Method> methods = new ArrayList<>();

        protected JavaType(Container.Entry entry, ClassReader classReader, final int outerAccess) {
            this.entry = entry;
            this.name = "";
            ClassVisitor classAndInnerClassesVisitor = new ClassVisitor(Opcodes.ASM7) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    setFlags(outerAccess == -1 ? access : outerAccess);
                    setName(name);
                    setSuperName((access & Opcodes.ACC_INTERFACE) != 0 && StringConstants.JAVA_LANG_OBJECT.equals(superName) ? null : superName);
                }

                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int access) {
                    if (getName().equals(name)) {
                        // Inner class path found
                        setOuterName(outerName);
                        setDisplayInnerTypeName(innerName);
                    } else if ((access & (Opcodes.ACC_SYNTHETIC|Opcodes.ACC_BRIDGE)) == 0 && getName().equals(outerName)) {
                        Container.Entry innerEntry = getEntry(name);

                        if (innerEntry != null) {
                            try (InputStream is = innerEntry.getInputStream()) {
                                ClassReader classReader = new ClassReader(is);
                                if (innerTypes == null) {
                                    innerTypes = new ArrayList<>();
                                }
                                innerTypes.add(new JavaType(innerEntry, classReader, access));
                            } catch (IOException e) {
                                assert ExceptionUtil.printStackTrace(e);
                            }
                        }
                    }
                }
            };

            classReader.accept(classAndInnerClassesVisitor, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);

            int lastPackageSeparatorIndex = name.lastIndexOf('/');

            if (lastPackageSeparatorIndex == -1) {
                displayPackageName = "";

                if (outerName == null) {
                    displayTypeName = name;
                } else {
                    displayTypeName = getDisplayTypeName(outerName, 0) + '.' + displayInnerTypeName;
                }
            } else {
                displayPackageName = name.substring(0, lastPackageSeparatorIndex).replace('/', '.');

                if (outerName == null) {
                    displayTypeName = name;
                } else {
                    displayTypeName = getDisplayTypeName(outerName, lastPackageSeparatorIndex) + '.' + displayInnerTypeName;
                }

                displayTypeName = displayTypeName.substring(lastPackageSeparatorIndex+1);
            }

            ClassVisitor fieldsAndMethodsVisitor = new ClassVisitor(Opcodes.ASM7) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ((access & (Opcodes.ACC_SYNTHETIC|Opcodes.ACC_ENUM)) == 0) {
                        fields.add(new Type.Field() {
                            @Override
                            public int getFlags() { return access; }
                            @Override
                            public String getName() { return name; }
                            @Override
                            public String getDescriptor() { return descriptor; }
                            @Override
                            public Icon getIcon() { return getFieldIcon(access); }

                            @Override
                            public String getDisplayName() {
                                StringBuilder sb = new StringBuilder();
                                sb.append(name).append(" : ");
                                writeSignature(sb, descriptor, descriptor.length(), 0, false);
                                return sb.toString();
                            }
                        });
                    }
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ((access & (Opcodes.ACC_SYNTHETIC|Opcodes.ACC_ENUM|Opcodes.ACC_BRIDGE)) == 0) {
                        methods.add(new Type.Method() {
                            @Override
                            public int getFlags() { return access; }
                            @Override
                            public String getName() { return name; }
                            @Override
                            public String getDescriptor() { return descriptor; }
                            @Override
                            public Icon getIcon() { return getMethodIcon(access); }

                            @Override
                            public String getDisplayName() {
                                boolean isInnerClass = getDisplayInnerTypeName() != null;
                                String constructorName = isInnerClass ? getDisplayInnerTypeName() : getDisplayTypeName();
                                StringBuilder sb = new StringBuilder();
                                writeMethodSignature(sb, getFlags(), access, isInnerClass, constructorName, name, descriptor);
                                return sb.toString();
                            }
                        });
                    }
                    return null;
                }
            };

            classReader.accept(fieldsAndMethodsVisitor, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }

        protected String getDisplayTypeName(String name, int packageLength) {
            int indexDollar = name.lastIndexOf('$');

            if (indexDollar > packageLength) {
                Container.Entry loadedEntry = getEntry(name);

                if (loadedEntry != null) {
                    try (InputStream is = loadedEntry.getInputStream()) {
                        ClassReader classReader = new ClassReader(is);
                        InnerClassVisitor classVisitor = new InnerClassVisitor(name);

                        classReader.accept(classVisitor, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);

                        String localOuterName = classVisitor.getOuterName();

                        if (localOuterName != null) {
                            // Inner class path found => Recursive call
                            return getDisplayTypeName(localOuterName, packageLength) + '.' + classVisitor.getInnerName();
                        }
                    } catch (IOException e) {
                        assert ExceptionUtil.printStackTrace(e);
                    }
                }
            }

            return name;
        }

        protected Container.Entry getEntry(String typeName) {
            return entry.getParent().getChildren().get(new FileEntryPath(typeName + StringConstants.CLASS_FILE_SUFFIX));
        }

        @Override
        public int getFlags() { return access; }
        @Override
        public String getName() { return name; }
        @Override
        public String getSuperName() { return superName; }
        @Override
        public String getOuterName() { return outerName; }
        @Override
        public String getDisplayPackageName() { return displayPackageName; }
        @Override
        public String getDisplayTypeName() { return displayTypeName; }
        @Override
        public String getDisplayInnerTypeName() { return displayInnerTypeName; }
        @Override
        public Icon getIcon() { return getTypeIcon(access); }
        @Override
        public List<Type> getInnerTypes() { return innerTypes; }
        @Override
        public List<Type.Field> getFields() { return fields; }
        @Override
        public List<Type.Method> getMethods() { return methods; }

        public Container.Entry getEntry() {
            return entry;
        }

        private void setFlags(int access) {
            this.access = access;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setSuperName(String superName) {
            this.superName = superName;
        }

        private void setOuterName(String outerName) {
            this.outerName = outerName;
        }

        private void setDisplayInnerTypeName(String displayInnerTypeName) {
            this.displayInnerTypeName = displayInnerTypeName;
        }

    }

    protected static class InnerClassVisitor extends ClassVisitor {
        private String name;
        private String outerName;
        private String innerName;

        public InnerClassVisitor(String name) {
            super(Opcodes.ASM7);
            this.name = name;
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (this.name.equals(name)) {
                // Inner class path found
                this.outerName = outerName;
                this.innerName = innerName;
            }
        }

        public String getOuterName() {
            return outerName;
        }

        public String getInnerName() {
            return innerName;
        }
    }
}
