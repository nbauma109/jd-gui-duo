/*
 * Copyright 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 */
package org.jd.gui.model.debug;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public final class DebugVariableNode {
    /** What the node represents; selects its icon (locals, array elements and {@code this} are LOCAL). */
    public enum Kind { LOCAL, FIELD_PUBLIC, FIELD_PROTECTED, FIELD_PRIVATE, FIELD_DEFAULT }

    public final String name;
    public final String valueText;
    public final Kind kind;
    private final Supplier<List<DebugVariableNode>> childLoader;
    private volatile List<DebugVariableNode> children;
    /** Full value as shown in the detail pane of the Inspect popup (e.g. the toString() of an object), or null. */
    private volatile String detailText;

    /** Leaf node (primitive, null, String, etc.) */
    public DebugVariableNode(String name, String valueText) {
        this(name, valueText, Kind.LOCAL);
    }

    public DebugVariableNode(String name, String valueText, Kind kind) {
        this.name = name;
        this.valueText = valueText;
        this.kind = kind;
        this.childLoader = null;
        this.children = Collections.emptyList();
    }

    /** Expandable object node with lazy child loading. */
    public DebugVariableNode(String name, String valueText, Supplier<List<DebugVariableNode>> childLoader) {
        this(name, valueText, Kind.LOCAL, childLoader);
    }

    public DebugVariableNode(String name, String valueText, Kind kind, Supplier<List<DebugVariableNode>> childLoader) {
        this.name = name;
        this.valueText = valueText;
        this.kind = kind;
        this.childLoader = childLoader;
        this.children = null;
    }

    public String getDetailText() {
        return detailText != null ? detailText : valueText;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText;
    }

    public boolean isLeaf() {
        return childLoader == null;
    }

    public boolean isChildrenLoaded() {
        return children != null;
    }

    /** Returns children, loading them on first call if necessary. */
    public synchronized List<DebugVariableNode> getChildren() {
        if (childLoader == null) {
            return Collections.emptyList();
        }
        if (children == null) {
            children = childLoader.get();
        }
        return children;
    }

    @Override
    public String toString() {
        return name + ": " + valueText;
    }
}
