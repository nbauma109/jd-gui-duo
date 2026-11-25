/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.Opcodes;

import java.util.Properties;

import javax.swing.ImageIcon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How the types and the methods of the tree are written, and which icon they have. */
class SignatureWritingTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "B | byte", "C | char", "D | double", "F | float", "I | int", "J | long", "S | short", "Z | boolean", "V | void",
            "[I | int[]", "[[Ljava/lang/String; | String[][]", "Ljava/util/Map$Entry; | Map.Entry", "LPlain; | Plain",
            "Ljava/util/List<Ljava/lang/String;>; | List<String>", "Ljava/util/Map<Ljava/lang/String;[I>; | Map<String, int[]>",
            "Ljava/util/List<+Ljava/lang/Number;>; | List<? extends Number>", "Ljava/util/List<-Ljava/lang/Integer;>; | List<? super Integer>",
            "Ljava/lang/Class<*>; | Class<?>", "La/Outer<*>.Inner; | Outer<?>.Inner"})
    void aDescriptorIsWrittenAsInTheSource(String descriptor, String expected) {
        StringBuilder written = new StringBuilder();

        int end = AbstractTypeFactoryProvider.writeSignature(written, descriptor, descriptor.length(), 0, false);

        assertThat(written).hasToString(expected);
        assertThat(end).isEqualTo(descriptor.length());
    }

    @Test
    void theLastDimensionOfVariableArgumentsIsWrittenAsDots() {
        StringBuilder written = new StringBuilder();

        AbstractTypeFactoryProvider.writeSignature(written, "[[I", 3, 0, true);

        assertThat(written).hasToString("int[]...");
    }

    @Test
    void whatIsNoDescriptorIsRefused() {
        StringBuilder written = new StringBuilder();

        assertThatThrownBy(() -> AbstractTypeFactoryProvider.writeSignature(written, "?", 1, 0, false)).isInstanceOf(IllegalStateException.class);
    }

    private static String method(int typeAccess, int methodAccess, boolean inner, String name, String descriptor) {
        StringBuilder written = new StringBuilder();
        AbstractTypeFactoryProvider.writeMethodSignature(written, typeAccess, methodAccess, inner, "Type", name, descriptor);
        return written.toString();
    }

    @Test
    void aMethodIsWrittenWithItsParametersAndWhatItReturns() {
        assertThat(method(0, 0, false, "run", "()V")).isEqualTo("run() : void");
        assertThat(method(0, 0, false, "of", "(ILjava/lang/String;)[J")).isEqualTo("of(int, String) : long[]");
        assertThat(method(0, Opcodes.ACC_VARARGS, false, "format", "(Ljava/lang/String;[Ljava/lang/Object;)V")).isEqualTo("format(String, Object...) : void");
        // the variable arguments may be the only parameter
        assertThat(method(0, Opcodes.ACC_VARARGS, false, "all", "([I)V")).isEqualTo("all(int...) : void");
        assertThat(method(0, Opcodes.ACC_VARARGS, true, "<init>", "(Ldemo/Outer;[[I)V")).isEqualTo("Type(int[]...)");
        assertThat(method(0, 0, false, "all", "([I)V")).isEqualTo("all(int[]) : void");
        assertThat(method(0, 0, false, "<clinit>", "()V")).isEqualTo("{...}");
    }

    @Test
    void theConstructorOfAnInnerClassLeavesOutItsOuterInstance() {
        assertThat(method(0, 0, false, "<init>", "(I)V")).isEqualTo("Type(int)");
        assertThat(method(0, 0, true, "<init>", "(Ldemo/Outer;I)V")).isEqualTo("Type(int)");
        assertThat(method(0, 0, true, "<init>", "(Ldemo/Outer;)V")).isEqualTo("Type()");
        // a static nested class has no outer instance
        assertThat(method(Opcodes.ACC_STATIC, 0, true, "<init>", "(Ldemo/Outer;)V")).isEqualTo("Type(Outer)");
    }

    @ParameterizedTest(name = "access {0} has icon {1}")
    @CsvSource({"0, 0", "1, 1", "4, 2", "2, 3", "8, 4", "17, 9", "1025, 17"})
    void theIconOfAMemberFollowsItsModifiers(int access, int index) {
        assertThat(AbstractTypeFactoryProvider.accessToIndex(access)).isEqualTo(index);
        assertThat(AbstractTypeFactoryProvider.getFieldIcon(access)).isNotNull();
        assertThat(AbstractTypeFactoryProvider.getMethodIcon(access)).isNotNull();
    }

    @Test
    void theKindsOfTypesHaveDifferentIcons() {
        ImageIcon type = AbstractTypeFactoryProvider.getTypeIcon(Opcodes.ACC_PUBLIC);

        assertThat(AbstractTypeFactoryProvider.getTypeIcon(Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE)).isNotSameAs(type);
        assertThat(AbstractTypeFactoryProvider.getTypeIcon(Opcodes.ACC_INTERFACE)).isNotSameAs(type);
        assertThat(AbstractTypeFactoryProvider.getTypeIcon(Opcodes.ACC_ENUM)).isNotSameAs(type);
    }

    @Test
    void theSelectorsAndThePathsCanBeSetFromOutside() {
        AbstractTypeFactoryProvider provider = new ClassFileTypeFactoryProvider();
        assertThat(provider.appendSelectors("*:file:*.x")).containsExactly("*:file:*.x");
        Properties properties = new Properties();
        properties.setProperty("selectors", "a,b");
        properties.setProperty("pathRegExp", "demo/.*");

        provider.init(properties);

        assertThat(provider.appendSelectors("c")).containsExactly("a", "b", "c");
        assertThat(provider.getPathPattern().matcher("demo/A.class").matches()).isTrue();
    }
}
