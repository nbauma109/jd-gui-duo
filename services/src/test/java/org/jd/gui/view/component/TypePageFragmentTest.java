/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import jd.core.links.DeclarationData;

import org.fife.ui.rtextarea.DocumentRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a click on a link selects in the declarations of a page: an exact descriptor, a wildcard one, and the hints of a call without binding. */
class TypePageFragmentTest {

    private static Map<String, DeclarationData> declarations(Object... startsAndDescriptors) {
        Map<String, DeclarationData> declarations = new LinkedHashMap<>();
        declarations.put("demo/A", new DeclarationData(0, 1, "demo/A", null, null));
        for (int i = 0; i < startsAndDescriptors.length; i += 3) {
            int start = (Integer) startsAndDescriptors[i];
            String name = (String) startsAndDescriptors[i + 1];
            String descriptor = (String) startsAndDescriptors[i + 2];
            declarations.put("demo/A-" + name + "-" + descriptor, new DeclarationData(start, 1, "demo/A", name, descriptor));
        }
        return declarations;
    }

    private static List<Integer> starts(String fragment, Map<String, DeclarationData> declarations) {
        List<DocumentRange> ranges = new ArrayList<>();
        TypePage.matchFragmentAndAddDocumentRange(fragment, declarations, ranges);
        return ranges.stream().map(DocumentRange::getStartOffset).sorted().toList();
    }

    private static final Map<String, DeclarationData> OVERLOADS = declarations(
            10, "f", "(Ljava/lang/String;)V", 20, "f", "(J)V", 30, "f", "(IILjava/lang/String;)V", 40, "f", "(Ljava/util/List;)V", 50, "<init>", "(Ljava/lang/String;)V");

    @Test
    void anExactDescriptorSelectsItsDeclaration() {
        assertThat(starts("demo/A-f-(J)V", OVERLOADS)).containsExactly(20);
        assertThat(starts("demo/A-f-(Ljava/lang/String;)V", OVERLOADS)).containsExactly(10);
        assertThat(starts("demo/A-f-(D)V", OVERLOADS)).isEmpty();
    }

    @Test
    void aWildcardDescriptorSelectsAllTheMatches() {
        assertThat(starts("demo/A-f-(?)V", OVERLOADS)).containsExactly(10, 20, 40);
        assertThat(starts("demo/A-f-(?)?", OVERLOADS)).containsExactly(10, 20, 40);
    }

    @Test
    void theHintsOfACallAreRequiredWhenAMethodHasThem() {
        // f("x") has a String: the method taking a String, not the other with one parameter
        assertThat(starts("demo/A-f-(~Ljava/lang/String;)?", OVERLOADS)).containsExactly(10);
        assertThat(starts("demo/A-f-(~I~I~Ljava/lang/String;)?", OVERLOADS)).containsExactly(30);
    }

    @Test
    void whenNoMethodHasTheHintsTheOnesWithAsManyParametersAreSelected() {
        // f(1) with an int: no method takes an int, but f(long) accepts it (and f(String) and f(List) are possible too)
        assertThat(starts("demo/A-f-(~I)?", OVERLOADS)).containsExactly(10, 20, 40);
        // an ArrayList is a List: the declared type is a supertype of the type of the argument
        assertThat(starts("demo/A-f-(~Ljava/util/ArrayList;)?", OVERLOADS)).containsExactly(10, 20, 40);
        assertThat(starts("demo/A-f-(~I~I~I~I)?", OVERLOADS)).isEmpty();
    }

    @Test
    void anUnknownArgumentMatchesAnyParameterAndAConstructorHasItsHintsToo() {
        assertThat(starts("demo/A-f-(?)?", OVERLOADS)).containsExactly(10, 20, 40);
        assertThat(starts("demo/A-<init>-(~Ljava/lang/String;)V", OVERLOADS)).containsExactly(50);
        assertThat(starts("demo/A-<init>-(~I)V", OVERLOADS)).containsExactly(50);
        assertThat(starts("demo/A-<init>-(~I~I)V", OVERLOADS)).isEmpty();
    }

    @Test
    void aReferenceWithoutBindingSelectsAllTheMethodsOfThatNameOrAllTheConstructors() {
        Map<String, DeclarationData> declarations = declarations(
                10, "f", "(Ljava/lang/String;)V", 20, "f", "(J)V", 30, "g", "(I)V", 40, "<init>", "(Ljava/lang/String;)V", 50, "<init>", "()V");

        assertThat(starts("demo/A-f-(*)?", declarations)).containsExactly(10, 20);
        assertThat(starts("demo/A-<init>-(*)V", declarations)).containsExactly(40, 50);
        assertThat(starts("demo/A-nothing-(*)?", declarations)).isEmpty();
    }
}
