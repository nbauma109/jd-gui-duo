package org.jd.gui.util.parser.jdt.core;

public class HyperlinkReferenceData extends HyperlinkData {
	
    private ReferenceData reference;

    public HyperlinkReferenceData(int startPosition, int length, ReferenceData reference) {
        super(startPosition, startPosition+length);
        this.reference = reference;
    }

	public ReferenceData getReference() {
		return reference;
	}
}