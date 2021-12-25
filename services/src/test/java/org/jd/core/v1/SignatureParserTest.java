/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.core.v1;

import org.jd.core.v1.loader.ClassPathLoader;
import org.jd.core.v1.loader.ZipLoader;
import org.jd.core.v1.model.classfile.ClassFile;
import org.jd.core.v1.model.javasyntax.type.*;
import org.jd.core.v1.model.message.DecompileContext;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.core.v1.service.deserializer.classfile.ClassFileDeserializer;
import org.jd.core.v1.services.javasyntax.type.visitor.PrintTypeVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

import junit.framework.TestCase;

public class SignatureParserTest extends TestCase {
    protected ClassFileDeserializer deserializer = new ClassFileDeserializer();

    @Test
    public void testAnnotatedClass() throws Exception {
        PrintTypeVisitor visitor = new PrintTypeVisitor();
        try (InputStream is = getClass().getResourceAsStream("/zip/data-java-jdk-1.7.0.zip")) {
            ZipLoader loader = new ZipLoader(is);
            TypeMaker typeMaker = new TypeMaker(loader);

            DecompileContext decompileContext = new DecompileContext();
            decompileContext.setMainInternalTypeName("org/jd/core/test/AnnotatedClass");
            decompileContext.setLoader(loader);

            ClassFile classFile = deserializer.loadClassFile(loader, decompileContext.getMainInternalTypeName());
            decompileContext.setClassFile(classFile);

            // Check type
            TypeMaker.TypeTypes typeTypes = typeMaker.parseClassFileSignature(classFile);

            // Check type parameterTypes
            assertNull(typeTypes.getTypeParameters());

            // Check super type
            assertNotNull(typeTypes.getSuperType());
            visitor.reset();

            BaseType superType = typeTypes.getSuperType();

            superType.accept(visitor);
            String source = visitor.toString();

            Assert.assertEquals("java.util.ArrayList", source);

            // Check interfaces
            assertNotNull(typeTypes.getInterfaces());
            visitor.reset();
            typeTypes.getInterfaces().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.io.Serializable, java.lang.Cloneable", source);

            // Check field 'list1'
            //  public List<List<? extends Generic>> list1
            BaseType type = typeMaker.parseFieldSignature(classFile, classFile.getFields()[0]);
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("boolean", source);

            // Check method 'add'
            //  public int add(int i1, int i2)
            TypeMaker.MethodTypes methodTypes = typeMaker.parseMethodSignature(classFile, classFile.getMethods()[1]);

            // Check type parameterTypes
            assertNull(methodTypes.getTypeParameters());

            // Check parameterTypes
            assertNotNull(methodTypes.getParameterTypes());
            assertEquals(2, methodTypes.getParameterTypes().size());

            type = methodTypes.getParameterTypes().getFirst();
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("int", source);

            // Check return type
            assertNotNull(methodTypes.getReturnedType());

            BaseType returnedType = methodTypes.getReturnedType();
            visitor.reset();
            returnedType.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("int", source);

            // Check exceptions
            assertNull(methodTypes.getExceptionTypes());

            // Check method 'ping'
            //  public void ping(String host) throws UnknownHostException, UnsatisfiedLinkError
            methodTypes = typeMaker.parseMethodSignature(classFile, classFile.getMethods()[2]);

            // Check type parameterTypes
            assertNull(methodTypes.getTypeParameters());

            // Check parameterTypes
            assertNotNull(methodTypes.getParameterTypes());
            assertEquals(3, methodTypes.getParameterTypes().size());

            type = methodTypes.getParameterTypes().getList().get(1);
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.lang.String", source);

            // Check return type
            assertNotNull(methodTypes.getReturnedType());

            returnedType = methodTypes.getReturnedType();
            visitor.reset();
            returnedType.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("void", source);

            // Check exceptions
            assertNotNull(methodTypes.getExceptionTypes());

            visitor.reset();
            methodTypes.getExceptionTypes().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.net.UnknownHostException, java.lang.UnsatisfiedLinkError", source);
        }
    }

    @Test
    public void testGenericClass() throws Exception {
        PrintTypeVisitor visitor = new PrintTypeVisitor();
        try (InputStream is = getClass().getResourceAsStream("/zip/data-java-jdk-1.7.0.zip")) {
            ZipLoader loader = new ZipLoader(is);
            TypeMaker typeMaker = new TypeMaker(loader);

            DecompileContext decompileContext = new DecompileContext();
            decompileContext.setMainInternalTypeName("org/jd/core/test/GenericClass");
            decompileContext.setLoader(loader);

            ClassFile classFile = deserializer.loadClassFile(loader, decompileContext.getMainInternalTypeName());
            decompileContext.setClassFile(classFile);

            // Check type
            TypeMaker.TypeTypes typeTypes = typeMaker.parseClassFileSignature(classFile);

            // Check type parameterTypes
            // See "org.jd.core.test.resources.java.Generic"
            //  T1:Ljava/lang/Object;
            //  T2:Ljava/lang/Object;
            //  T3:Lorg/jd/core/v1/test/resources/java/AnnotatedClass;
            //  T4::Ljava/io/Serializable;
            //  T5::Ljava/io/Serializable;:Ljava/lang/Comparable;
            //  T6:Lorg/jd/core/v1/test/resources/java/AnnotatedClass;:Ljava/io/Serializable;:Ljava/lang/Comparable<Lorg/jd/core/v1/test/resources/java/GenericClass;>;
            //  T7::Ljava/util/Map<**>;
            //  T8::Ljava/util/Map<+Ljava/lang/Number;-Ljava/io/Serializable;>;
            //  T9:TT8;
            assertNotNull(typeTypes.getTypeParameters());
            typeTypes.getTypeParameters().accept(visitor);

            String source = visitor.toString();
            String expected =
                    "T1, " +
                            "T2, " +
                            "T3 extends org.jd.core.test.AnnotatedClass, " +
                            "T4 extends java.io.Serializable, " +
                            "T5 extends java.io.Serializable & java.lang.Comparable, " +
                            "T6 extends org.jd.core.test.AnnotatedClass & java.io.Serializable & java.lang.Comparable<org.jd.core.test.GenericClass>, " +
                            "T7 extends java.util.Map<?, ?>, " +
                            "T8 extends java.util.Map<? extends java.lang.Number, ? super java.io.Serializable>, " +
                            "T9 extends T8";

            Assert.assertEquals(expected, source);

            // Check super type
            BaseType superType = typeTypes.getSuperType();
            assertNotNull(superType);
            visitor.reset();
            superType.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.ArrayList<T7>", source);

            // Check interfaces
            assertNotNull(typeTypes.getInterfaces());
            visitor.reset();
            typeTypes.getInterfaces().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.io.Serializable, java.lang.Comparable<T1>", source);

            // Check field 'list1'
            //  public List<List<? extends Generic>> list1
            BaseType type = typeMaker.parseFieldSignature(classFile, classFile.getFields()[0]);
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.List<java.util.List<? extends org.jd.core.test.GenericClass>>", source);

            // Check method 'copy2'
            //  public <T, S extends T> List<? extends Number> copy2(List<? super T> dest, List<S> src) throws InvalidParameterException, ClassCastException
            TypeMaker.MethodTypes methodTypes = typeMaker.parseMethodSignature(classFile, classFile.getMethods()[3]);

            // Check type parameterTypes
            assertNotNull(methodTypes.getTypeParameters());
            visitor.reset();
            methodTypes.getTypeParameters().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("T, S extends T", source);

            // Check parameterTypes
            assertNotNull(methodTypes.getParameterTypes());
            assertEquals(2, methodTypes.getParameterTypes().size());

            type = methodTypes.getParameterTypes().getFirst();
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.List<? super T>", source);

            // Check return type
            assertNotNull(methodTypes.getReturnedType());

            BaseType returnedType = methodTypes.getReturnedType();
            visitor.reset();
            returnedType.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.List<? extends java.lang.Number>", source);

            // Check exceptions
            assertNotNull(methodTypes.getExceptionTypes());

            visitor.reset();
            methodTypes.getExceptionTypes().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.security.InvalidParameterException, java.lang.ClassCastException", source);

            // Check method 'print'
            //  public <T1, T2 extends Exception> List<? extends Number> print(List<? super T1> list) throws InvalidParameterException, T2
            methodTypes = typeMaker.parseMethodSignature(classFile, classFile.getMethods()[4]);

            // Check type parameterTypes
            assertNotNull(methodTypes.getTypeParameters());
            visitor.reset();
            methodTypes.getTypeParameters().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("T1, T2 extends java.lang.Exception", source);

            // Check parameterTypes
            assertNotNull(methodTypes.getParameterTypes());
            assertEquals(1, methodTypes.getParameterTypes().size());

            type = methodTypes.getParameterTypes().getFirst();
            visitor.reset();
            type.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.List<? super T1>", source);

            // Check return type
            assertNotNull(methodTypes.getReturnedType());

            returnedType = methodTypes.getReturnedType();
            visitor.reset();
            returnedType.accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("java.util.List<? extends java.lang.Number>", source);

            // Check exceptions
            assertNotNull(methodTypes.getExceptionTypes());

            visitor.reset();
            methodTypes.getExceptionTypes().accept(visitor);
            source = visitor.toString();

            Assert.assertEquals("T2, java.security.InvalidParameterException", source);
        }
    }

    @Test
    public void testParseReturnedVoid() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/zip/data-java-jdk-1.7.0.zip")) {
            ZipLoader loader = new ZipLoader(is);
            TypeMaker typeMaker = new TypeMaker(loader);

            Assert.assertEquals(PrimitiveType.TYPE_VOID, typeMaker.makeMethodTypes("()V").getReturnedType());
        }
    }

    @Test
    public void testParseReturnedPrimitiveType() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/zip/data-java-jdk-1.7.0.zip")) {
            ZipLoader loader = new ZipLoader(is);
            TypeMaker typeMaker = new TypeMaker(loader);

            Assert.assertEquals(PrimitiveType.TYPE_BOOLEAN, typeMaker.makeMethodTypes("()Z").getReturnedType());
        }
    }

    @Test
    public void testParseReturnedStringType() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/zip/data-java-jdk-1.7.0.zip")) {
            ZipLoader loader = new ZipLoader(is);
            TypeMaker typeMaker = new TypeMaker(loader);

            Assert.assertEquals(ObjectType.TYPE_STRING, typeMaker.makeMethodTypes("()Ljava/lang/String;").getReturnedType());
        }
    }

    @Test
    public void testGenericInnerClass() throws Exception {
        ClassPathLoader loader = new ClassPathLoader();
        TypeMaker typeMaker = new TypeMaker(loader);

        Type type = typeMaker.makeFromSignature("Lorg/apache/commons/collections4/multimap/AbstractMultiValuedMap<TK;TV;>.AsMap.AsMapEntrySetIterator;");

        Assert.assertEquals("Lorg/apache/commons/collections4/multimap/AbstractMultiValuedMap$AsMap$AsMapEntrySetIterator;", type.getDescriptor());
        Assert.assertEquals("Lorg/apache/commons/collections4/multimap/AbstractMultiValuedMap$AsMap$AsMapEntrySetIterator;", type.getDescriptor());

        ObjectType ot = (ObjectType)type;

        Assert.assertEquals("org/apache/commons/collections4/multimap/AbstractMultiValuedMap$AsMap$AsMapEntrySetIterator", ot.getInternalName());
        Assert.assertEquals("org.apache.commons.collections4.multimap.AbstractMultiValuedMap.AsMap.AsMapEntrySetIterator", ot.getQualifiedName());
        Assert.assertEquals("AsMapEntrySetIterator", ot.getName());
        Assert.assertNull(ot.getTypeArguments());

        ot = ((InnerObjectType)ot).getOuterType();

        Assert.assertEquals("org/apache/commons/collections4/multimap/AbstractMultiValuedMap$AsMap", ot.getInternalName());
        Assert.assertEquals("org.apache.commons.collections4.multimap.AbstractMultiValuedMap.AsMap", ot.getQualifiedName());
        Assert.assertEquals("AsMap", ot.getName());
        Assert.assertNull(ot.getTypeArguments());

        ot = ((InnerObjectType)ot).getOuterType();

        Assert.assertEquals("org/apache/commons/collections4/multimap/AbstractMultiValuedMap", ot.getInternalName());
        Assert.assertEquals("org.apache.commons.collections4.multimap.AbstractMultiValuedMap", ot.getQualifiedName());
        Assert.assertEquals("AbstractMultiValuedMap", ot.getName());
        Assert.assertNotNull(ot.getTypeArguments());

        TypeArguments typeArguments = (TypeArguments)ot.getTypeArguments();

        Assert.assertEquals(2, typeArguments.size());
        Assert.assertEquals("GenericType{K}", typeArguments.getFirst().toString());
        Assert.assertEquals("GenericType{V}", typeArguments.getLast().toString());
    }
}