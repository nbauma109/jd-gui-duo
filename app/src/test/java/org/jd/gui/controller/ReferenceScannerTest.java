/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_CONSTRUCTOR;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_FIELD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_METHOD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_STRING;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceScannerTest {

    private static ReferenceScanner.Result scan(String pattern, int flags) throws IOException {
        try (InputStream in = ReferenceScannerFixture.class.getResourceAsStream("ReferenceScannerFixture.class")) {
            return ReferenceScanner.scan(in, SearchInConstantPoolsController.createPattern(pattern), flags);
        }
    }

    @Test
    void methodReferenceIsFoundInTheCallingMethodOnly() throws IOException {
        assertEquals(Set.of("callsMethod()V"), scan("valueOf", SEARCH_METHOD).methods());
    }

    @Test
    void fieldReferenceIsFoundInTheAccessingMethods() throws IOException {
        assertEquals(Set.of("readsField()V", "usesString()V"), scan("name", SEARCH_FIELD).methods());
    }

    @Test
    void stringConstantIsFoundInItsMethod() throws IOException {
        assertEquals(Set.of("usesString()V"), scan("needle*", SEARCH_STRING).methods());
    }

    @Test
    void constructorReferenceMatchesTheTypeSimpleName() throws IOException {
        assertEquals(Set.of("buildsType()V"), scan("StringBuilder", SEARCH_CONSTRUCTOR).methods());
    }

    @Test
    void typeNameIsNotAMethodMatch() throws IOException {
        assertTrue(scan("StringBuilder", SEARCH_METHOD).methods().isEmpty());
    }

    @Test
    void typeReferenceCoversInstructionsThrowsClausesAndFieldDescriptors() throws IOException {
        assertTrue(scan("StringBuilder", SEARCH_TYPE).methods().contains("buildsType()V"));
        assertTrue(scan("IOException", SEARCH_TYPE).methods().contains("throwsType()V"));
        assertEquals(Set.of("itemsLjava/util/List;", "buildersLjava/util/List;"), scan("List", SEARCH_TYPE).fields());
    }

    @Test
    void typeOnlyInAGenericSignatureIsAttributedToItsMembers() throws IOException {
        // StringBuilder is erased from the field descriptor (Ljava/util/List;) and kept in its signature
        assertTrue(scan("StringBuilder", SEARCH_TYPE).fields().contains("buildersLjava/util/List;"));
        assertTrue(scan("ProcessBuilder", SEARCH_TYPE).methods().contains("genericReturn(Ljava/util/List;)Ljava/util/List;"));
        assertTrue(scan("Integer", SEARCH_TYPE).methods().contains("genericReturn(Ljava/util/List;)Ljava/util/List;"));
    }

    @Test
    void annotationTypesAreAttributedToTheirMembers() throws IOException {
        ReferenceScanner.Result result = scan("Marker", SEARCH_TYPE);
        assertTrue(result.fields().contains("markedI"));
        assertTrue(result.methods().contains("markedMethod()V"));
        assertTrue(result.methods().contains("markedParameter(I)V"));
        assertFalse(result.methods().contains("untouched()V"));
    }

    @Test
    void classValueOfAnAnnotationIsAttributedToItsMember() throws IOException {
        assertEquals(Set.of("markedClassValue()V"), scan("Thread", SEARCH_TYPE).methods());
    }

    @Test
    void invokedynamicBootstrapMethodIsAttributedToTheContainingMethod() throws IOException {
        assertEquals(Set.of("lambdaUser()Ljava/lang/Runnable;"), scan("metafactory", SEARCH_METHOD).methods());
        assertTrue(scan("LambdaMetafactory", SEARCH_TYPE).methods().contains("lambdaUser()Ljava/lang/Runnable;"));
    }

    @Test
    void dynamicConstantBootstrapIsAttributedToTheContainingMethod() throws IOException {
        // javac does not emit "ldc" of a dynamic constant, so the class is generated
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "generated/Condy", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dynamicHolder", "()Ljava/lang/Object;", null, null);
        method.visitCode();
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/ConstantBootstraps", "nullConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", false);
        method.visitLdcInsn(new ConstantDynamic("unused", "Ljava/lang/Object;", bootstrap));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();

        ReferenceScanner.Result byMethod = ReferenceScanner.scan(new ByteArrayInputStream(bytes), SearchInConstantPoolsController.createPattern("nullConstant"), SEARCH_METHOD);
        ReferenceScanner.Result byType = ReferenceScanner.scan(new ByteArrayInputStream(bytes), SearchInConstantPoolsController.createPattern("ConstantBootstraps"), SEARCH_TYPE);

        assertEquals(Set.of("dynamicHolder()Ljava/lang/Object;"), byMethod.methods());
        assertEquals(Set.of("dynamicHolder()Ljava/lang/Object;"), byType.methods());
    }

    @Test
    void methodWithoutMatchingReferenceIsNotReported() throws IOException {
        assertFalse(scan("*", SEARCH_METHOD | SEARCH_FIELD | SEARCH_STRING).methods().contains("untouched()V"));
    }
}
