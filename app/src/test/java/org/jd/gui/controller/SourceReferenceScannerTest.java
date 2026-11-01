/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_CONSTRUCTOR;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_FIELD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_METHOD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_STRING;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceReferenceScannerTest {
    private static final String SAMPLE = """
            package demo;

            import java.util.List;

            public class Sample {
                List<String> items;
                int count = compute();
                int first = 1, second = Helper.compute();

                void greet() {
                    System.out.println("Hello");
                }

                void other(List<String> parameter) {
                }

                void callsAnonymous() {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            System.out.println("anonymous");
                        }
                    };
                }

                static {
                    init();
                }

                class Nested {
                    void inner() {
                        new StringBuilder();
                        this.hidden = 1;
                    }
                    int hidden;
                    void access() {
                        this.hidden = 2;
                    }
                }

                int compute() {
                    return 0;
                }

                static void init() {
                }
            }
            """;

    private static Map<String, ReferenceScanner.Result> scan(String pattern, int flags) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(SAMPLE.toCharArray());
        return SourceReferenceScanner.scan(parser, entry(), SearchInConstantPoolsController.createPattern(pattern), flags);
    }

    @Test
    void stringLiteralIsAttributedToItsMethod() {
        assertEquals(Set.of("greet()V"), scan("Hello", SEARCH_STRING).get("demo/Sample").methods());
    }

    @Test
    void methodCallIsAttributedToTheInnermostMethod() {
        // The call in the anonymous class is made by its own run() method, not by callsAnonymous()
        assertEquals(Set.of("greet()V", "run()V"), scan("println", SEARCH_METHOD).get("demo/Sample").methods());
    }

    @Test
    void referenceInAFieldInitializerIsAttributedToTheField() {
        ReferenceScanner.Result result = scan("compute", SEARCH_METHOD).get("demo/Sample");
        assertTrue(result.fields().contains("countI"));
        assertTrue(result.fields().contains("secondI"));
        // "first = 1" holds no call: the initializer of one declarator does not belong to its siblings
        assertFalse(result.fields().contains("firstI"));
        assertEquals(Set.of(), result.methods());
    }

    @Test
    void typeReferenceCoversFieldTypesAndMethodSignatures() {
        ReferenceScanner.Result result = scan("List", SEARCH_TYPE).get("demo/Sample");
        assertEquals(Set.of("itemsLjava/util/List;"), result.fields());
        assertEquals(Set.of("other(Ljava/util/List;)V"), result.methods());
    }

    @Test
    void referenceInAnInitializerOnlyMarksTheType() {
        ReferenceScanner.Result result = scan("init", SEARCH_METHOD).get("demo/Sample");
        assertEquals(Set.of(), result.methods());
        assertEquals(Set.of(), result.fields());
    }

    @Test
    void referenceInANestedTypeIsAttributedToTheNestedType() {
        Map<String, ReferenceScanner.Result> results = scan("StringBuilder", SEARCH_CONSTRUCTOR);
        assertEquals(Set.of("inner()V"), results.get("demo/Sample$Nested").methods());
        assertFalse(results.containsKey("demo/Sample"));
    }

    @Test
    void fieldAccessIsAttributedToItsMethods() {
        assertEquals(Set.of("inner()V", "access()V"), scan("hidden", SEARCH_FIELD).get("demo/Sample$Nested").methods());
    }

    @Test
    void referenceInAnAnonymousClassIsAttributedToTheEnclosingMethodNode() {
        // The type factory lists the anonymous class methods under the enclosing type, so "run()V" is a node
        assertTrue(scan("anonymous", SEARCH_STRING).get("demo/Sample").methods().contains("run()V"));
    }

    @Test
    void searchWithoutTheMatchingKindFindsNothing() {
        assertTrue(scan("Hello", SEARCH_METHOD).isEmpty());
    }

    private static Container.Entry entry() {
        Container.Entry root = (Container.Entry) Proxy.newProxyInstance(Container.Entry.class.getClassLoader(), new Class<?>[] { Container.Entry.class },
                (p, m, a) -> "getChildren".equals(m.getName()) ? Map.of() : null);
        Container container = (Container) Proxy.newProxyInstance(Container.class.getClassLoader(), new Class<?>[] { Container.class },
                (p, m, a) -> "getRoot".equals(m.getName()) ? root : null);
        return (Container.Entry) Proxy.newProxyInstance(Container.Entry.class.getClassLoader(), new Class<?>[] { Container.Entry.class },
                (p, m, a) -> switch (m.getName()) {
                    case "getPath" -> "Sample.java";
                    case "getContainer" -> container;
                    default -> null;
                });
    }
}
