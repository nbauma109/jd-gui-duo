/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.model.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {

    private History history;

    @BeforeEach
    void setUp() {
        history = new History();
    }

    @Test
    void backward_returnsNullWhenHistoryIsEmpty() {
        assertNull(history.backward());
    }

    @Test
    void forward_returnsNullWhenHistoryIsEmpty() {
        assertNull(history.forward());
    }

    @Test
    void canBackward_returnsFalseInitially() {
        assertFalse(history.canBackward());
    }

    @Test
    void canForward_returnsFalseInitially() {
        assertFalse(history.canForward());
    }

    @Test
    void add_firstUri_initializes_current() {
        URI uri = URI.create("file:///a/b/C.class");
        history.add(uri);

        assertFalse(history.canBackward());
        assertFalse(history.canForward());
    }

    @Test
    void add_samePath_differentFragment_movesToNewFragment() {
        URI base = URI.create("file:///a/b/C.class");
        URI withFrag = URI.create("file:///a/b/C.class#method");
        history.add(base);
        history.add(withFrag);

        // Current should now be the fragment URI
        assertFalse(history.canBackward(),
                "Same path without prior fragment should not push to backward stack");
        assertFalse(history.canForward());
    }

    @Test
    void add_differentPaths_pushesBackward() {
        URI a = URI.create("file:///a/A.class");
        URI b = URI.create("file:///a/B.class");
        history.add(a);
        history.add(b);

        assertTrue(history.canBackward());
        assertFalse(history.canForward());
    }

    @Test
    void backward_returnsToFirstUri() {
        URI a = URI.create("file:///a/A.class");
        URI b = URI.create("file:///a/B.class");
        history.add(a);
        history.add(b);

        URI previous = history.backward();
        assertEquals(a, previous);
    }

    @Test
    void backward_thenForward_returnsToOriginalUri() {
        URI a = URI.create("file:///a/A.class");
        URI b = URI.create("file:///a/B.class");
        history.add(a);
        history.add(b);

        history.backward();
        URI next = history.forward();

        assertEquals(b, next);
    }

    @Test
    void add_clearForwardStack_afterNewNavigation() {
        URI a = URI.create("file:///a/A.class");
        URI b = URI.create("file:///a/B.class");
        URI c = URI.create("file:///a/C.class");
        history.add(a);
        history.add(b);
        history.backward();
        // Forward should be available now
        assertTrue(history.canForward());

        // Navigate to a new URI; should clear forward
        history.add(c);
        assertFalse(history.canForward());
    }

    @Test
    void add_duplicateUri_isIgnored() {
        URI a = URI.create("file:///a/A.class");
        history.add(a);
        history.add(a);

        assertFalse(history.canBackward());
    }

    @Test
    void add_childUri_replacesCurrentUri() {
        URI parent = URI.create("file:///a/A.class");
        URI child = URI.create("file:///a/A.class#inner");
        history.add(parent);
        history.add(child);

        // Navigating from parent to child should not push to backward
        assertFalse(history.canBackward());
    }

    @Test
    void add_parentUri_isIgnored() {
        URI child = URI.create("file:///a/A.class#inner");
        URI parent = URI.create("file:///a/A.class");
        history.add(child);
        history.add(parent);

        // Adding a parent URI while a child is current should do nothing
        assertFalse(history.canBackward());
    }

    @Test
    void multipleBackwardForward_navigatesCorrectly() {
        URI a = URI.create("file:///a/A.class");
        URI b = URI.create("file:///a/B.class");
        URI c = URI.create("file:///a/C.class");
        history.add(a);
        history.add(b);
        history.add(c);

        assertEquals(b, history.backward());
        assertEquals(a, history.backward());
        assertFalse(history.canBackward());

        assertEquals(b, history.forward());
        assertEquals(c, history.forward());
        assertFalse(history.canForward());
    }

    @Test
    void add_samePathWithFragment_whenCurrentAlreadyHasFragment_pushesBackward() {
        URI base = URI.create("file:///a/b/C.class");
        URI frag1 = URI.create("file:///a/b/C.class#method1");
        URI frag2 = URI.create("file:///a/b/C.class#method2");
        history.add(base);
        history.add(frag1);
        history.add(frag2);

        // Going from frag1 to frag2 (same path, both have fragments) should push to backward
        assertTrue(history.canBackward());
        assertEquals(frag1, history.backward());
    }
}
