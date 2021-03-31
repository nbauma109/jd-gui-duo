package org.jd.gui.util.parser.jdt.core;

public class StringData {
	
    private int startPosition;
    private String text;
    private String owner;

    public StringData(int startPosition, String text, String owner) {
        this.startPosition = startPosition;
        this.text = text;
        this.owner = owner;
    }

	public String getOwner() {
		return owner;
	}

	public String getText() {
		return text;
	}

	public int getStartPosition() {
		return startPosition;
	}
}