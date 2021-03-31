package org.jd.gui.util.decompiler.postprocess;

public interface PostProcess {

	String COMMENT_END = "*/";
	String COMMENT_START = "/*";

	String process(String input);
}
