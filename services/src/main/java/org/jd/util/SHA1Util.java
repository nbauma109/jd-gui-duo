/*******************************************************************************
 * © 2022 Nicolas Baumann (@nbauma109)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.util;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SHA1Util {

    /** A line of sha1sum: the hash, white space, an asterisk in binary mode only, and the name of the file. */
    private static final Pattern CHECKSUM_LINE = Pattern.compile("(\\p{XDigit}{40})\\s++\\*?+(.++)");

    private SHA1Util() {
    }

    public static Map<File, String> readSHA1File(File file) {
        Map<File, String> sha1Map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = CHECKSUM_LINE.matcher(line);
                if (matcher.matches()) {
                    sha1Map.put(new File(matcher.group(2)), matcher.group(1));
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return sha1Map;
    }

    public static String computeSHA1(File file) {
        MessageDigest messageDigest;
        StringBuilder sb = new StringBuilder();
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1024 * 2];

            try (DigestInputStream is = new DigestInputStream(new FileInputStream(file), messageDigest)) {
                while (is.read(buffer) > -1) {
                    // read fully
                }
            }

            byte[] array = messageDigest.digest();

            for (byte b : array) {
                sb.append(hexa((b & 255) >> 4));
                sb.append(hexa(b & 15));
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return sb.toString();
    }

    private static char hexa(int i) {
        return (char) (i <= 9 ? '0' + i : 'a' - 10 + i);
    }

}
