package org.jd.gui.util;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class IOUtils {

	public static final int EOF = -1;
	public static final int DEFAULT_BUFFER_SIZE = 8192;

	public static char[] toCharArray(final InputStream is) throws IOException {
		return toCharArray(is, StandardCharsets.UTF_8);
	}

	public static String toString(final InputStream input) throws IOException {
		return toString(input, StandardCharsets.UTF_8);
	}

	public static char[] toCharArray(final InputStream input, final Charset charset) throws IOException {
		if (input == null) {
			return null;
		}
		try (final CharArrayWriter output = new CharArrayWriter()) {
			copy(input, output, charset);
			return output.toCharArray();
		} finally {
			input.close();
		}
	}

	public static String toString(final InputStream input, final Charset charset) throws IOException {
		if (input == null) {
			return null;
		}
		try (final StringWriter sw = new StringWriter()) {
			copy(input, sw, charset);
			return sw.toString();
		} finally {
			input.close();
		}
	}

	public static byte[] toByteArray(final InputStream input) throws IOException {
		if (input == null) {
			return null;
		}
		try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			copy(input, output);
			return output.toByteArray();
		} finally {
			input.close();
		}
	}

	private static void copy(final InputStream input, final Writer output, final Charset inputCharset)
			throws IOException {
		final InputStreamReader in = new InputStreamReader(input, inputCharset);
		copy(in, output);
	}

	private static long copy(final InputStream input, final OutputStream output, final int bufferSize)
			throws IOException {
		return copy(input, output, new byte[bufferSize]);
	}

	private static long copy(final InputStream input, final OutputStream output, final byte[] buffer)
			throws IOException {
		long count = 0;
		if (input != null) {
			for (int n; EOF != (n = input.read(buffer)); count += n) {
				output.write(buffer, 0, n);
			}
		}
		return count;
	}

	public static long copy(final InputStream input, final OutputStream output) throws IOException {
		return copy(input, output, DEFAULT_BUFFER_SIZE);
	}

	private static long copy(final Reader input, final Writer output) throws IOException {
		return copy(input, output, new char[DEFAULT_BUFFER_SIZE]);
	}

	private static long copy(final Reader input, final Writer output, final char[] buffer) throws IOException {
		long count = 0;
		for (int n; EOF != (n = input.read(buffer)); count += n) {
			output.write(buffer, 0, n);
		}
		return count;
	}

}
