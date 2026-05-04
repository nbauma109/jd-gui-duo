/*******************************************************************************
 *
 * © 2025-2026 Nicolas Baumann (@nbauma109)
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
package org.jd.gui.util.maven.central.helper;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigTest {

    @Test
    void isValid_returnsTrueForValidHostAndPort() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, null, null);
        assertTrue(config.isValid());
    }

    @Test
    void isValid_returnsTrueWhenPortIsNull() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", null, null, null);
        assertTrue(config.isValid());
    }

    @Test
    void isValid_returnsFalseWhenHostIsNull() {
        ProxyConfig config = new ProxyConfig(null, 8080, null, null);
        assertFalse(config.isValid());
    }

    @Test
    void isValid_returnsFalseWhenHostIsBlank() {
        ProxyConfig config = new ProxyConfig("   ", 8080, null, null);
        assertFalse(config.isValid());
    }

    @Test
    void isValid_returnsFalseWhenPortIsZero() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 0, null, null);
        assertFalse(config.isValid());
    }

    @Test
    void isValid_returnsFalseWhenPortIsAboveMaximum() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 65536, null, null);
        assertFalse(config.isValid());
    }

    @Test
    void isValid_returnsTrueForBoundaryPorts() {
        assertTrue(new ProxyConfig("h", 1, null, null).isValid());
        assertTrue(new ProxyConfig("h", 65535, null, null).isValid());
    }

    @Test
    void toJavaNetProxy_returnsConfiguredProxy() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 3128, null, null);
        Proxy proxy = config.toJavaNetProxy();

        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("proxy.example.com", address.getHostString());
        assertEquals(3128, address.getPort());
    }

    @Test
    void toJavaNetProxy_usesPort80WhenPortIsNull() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", null, null, null);
        Proxy proxy = config.toJavaNetProxy();

        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals(80, address.getPort());
    }

    @Test
    void toJavaNetProxy_returnsNoProxyWhenHostIsNull() {
        ProxyConfig config = new ProxyConfig(null, 8080, null, null);
        assertEquals(Proxy.NO_PROXY, config.toJavaNetProxy());
    }

    @Test
    void basicAuthHeaderValue_returnsNullWhenNoCredentials() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, null, null);
        assertNull(config.basicAuthHeaderValue());
    }

    @Test
    void basicAuthHeaderValue_returnsNullWhenPasswordIsEmpty() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "user", new char[0]);
        assertNull(config.basicAuthHeaderValue());
    }

    @Test
    void basicAuthHeaderValue_returnsNullWhenUsernameIsNull() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, null, "pass".toCharArray());
        assertNull(config.basicAuthHeaderValue());
    }

    @Test
    void basicAuthHeaderValue_returnsProperBasicHeaderForValidCredentials() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "alice", "secret".toCharArray());
        String header = config.basicAuthHeaderValue();

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("alice:secret".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(expected, header);
    }

    @Test
    void host_isTrimmed() {
        ProxyConfig config = new ProxyConfig("  proxy.example.com  ", 8080, null, null);
        assertEquals("proxy.example.com", config.host);
    }

    @Test
    void username_isTrimmed() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "  alice  ", null);
        assertEquals("alice", config.username);
    }

    @Test
    void username_isNullWhenBlank() {
        ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "   ", null);
        assertNull(config.username);
    }
}
