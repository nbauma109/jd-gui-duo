package org.jd.gui.util.decompiler.postprocess.impl;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.util.decompiler.postprocess.PostProcess;

public class LineNumberRealigner implements PostProcess {

	public void realign(Reader in, Writer out) {
		try (LineNumberReader lnr = new LineNumberReader(in); BufferedWriter bw = new BufferedWriter(out)) {
			String line;
			String emptyLineComment = "";
			int writerPosition = 1;
			while ((line = lnr.readLine()) != null) {
				if (line.startsWith(COMMENT_START)) {
					int idx = line.indexOf(COMMENT_END);
					if (idx != -1) {
						String commentContent = line.substring(2, idx);
						if (commentContent.matches("\\s+")) {
							emptyLineComment = commentContent;
							String lineContent = line.substring(idx + 2).trim();
							if (lineContent.isEmpty() || lineContent.startsWith("//")) {
								continue;
							}
						} else {
							String lineNumberComment = commentContent.trim();
							if (lineNumberComment.matches("\\d+")) {
								int expectedLineNumber = Integer.parseInt(lineNumberComment);
								while (writerPosition < expectedLineNumber) {
									bw.write(COMMENT_START);
									bw.write(emptyLineComment);
									bw.write(COMMENT_END);
									bw.newLine();
									writerPosition++;
								}
							}
						}
					}
				}
				bw.write(line);
				bw.newLine();
				writerPosition++;
			}
		} catch (IOException e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}

	@Override
	public String process(String in) {
		StringReader stringReader = new StringReader(in);
		StringWriter stringWriter = new StringWriter();
		realign(stringReader, stringWriter);
		return stringWriter.toString();
	}

	public static void main(String[] args) throws Exception {
		new LineNumberRealigner().realign(new FileReader(args[0]), new FileWriter(args[0] + ".tmp"));
	}

}
