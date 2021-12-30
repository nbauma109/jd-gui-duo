/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.history;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class History {
    private URI            current = null;
    private final List<URI> backward = new ArrayList<>();
    private final List<URI> forward = new ArrayList<>();

    public void add(URI uri) {
        if (current == null) {
            // Init history
            forward.clear();
            current = uri;
            return;
        }

        if (current.equals(uri)) {
            // Already stored -> Nothing to do
            return;
        }

        if (uri.getPath().equals(current.getPath())) {
            if (uri.getFragment() == null && uri.getQuery() == null) {
                // Ignore
            } else {
                if (current.getFragment() != null || current.getQuery() != null) {
                    // Store URI
                    forward.clear();
                    backward.add(current);
                }
                // Replace current URI
                current = uri;
            }
            return;
        }

        if (uri.toString().startsWith(current.toString())) {
            // Replace current URI
            current = uri;
            return;
        }

        if (current.toString().startsWith(uri.toString())) {
            // Parent URI -> Nothing to do
            return;
        }

        // Store URI
        forward.clear();
        backward.add(current);
        current = uri;
    }

    public URI backward() {
        if (! backward.isEmpty()) {
            forward.add(current);
            int size = backward.size();
            current = backward.remove(size-1);
        }
        return current;
    }

    public URI forward() {
        if (! forward.isEmpty()) {
            backward.add(current);
            int size = forward.size();
            current = forward.remove(size-1);
        }
        return current;
    }

    public boolean canBackward() { return !backward.isEmpty(); }
    public boolean canForward() { return !forward.isEmpty(); }
}
