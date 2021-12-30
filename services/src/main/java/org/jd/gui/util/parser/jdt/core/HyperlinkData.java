package org.jd.gui.util.parser.jdt.core;

public class HyperlinkData {

    private final int startPosition;
    private final int endPosition;
    private boolean enabled;

    public HyperlinkData(int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
