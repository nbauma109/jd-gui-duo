/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.maven.central.helper;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyConfigTest {

    @Test
    void blankHostAndUserAreNone() {
        ProxyConfig config = new ProxyConfig("  ", 8080, " ", null);

        assertThat(config.host).isNull();
        assertThat(config.username).isNull();
        assertThat(config.isValid()).isFalse();
        assertThat(config.toJavaNetProxy()).isSameAs(Proxy.NO_PROXY);
    }

    @Test
    void theHostAndTheUserAreTrimmed() {
        ProxyConfig config = new ProxyConfig("  proxy.example.org ", 3128, " bob ", "secret".toCharArray());

        assertThat(config.host).isEqualTo("proxy.example.org");
        assertThat(config.username).isEqualTo("bob");
        assertThat(config.port).isEqualTo(3128);
    }

    @Test
    void aProxyNeedsAHostAndAValidPortWhenThereIsOne() {
        assertThat(new ProxyConfig("h", null, null, null).isValid()).isTrue();
        assertThat(new ProxyConfig("h", 1, null, null).isValid()).isTrue();
        assertThat(new ProxyConfig("h", 65535, null, null).isValid()).isTrue();
        assertThat(new ProxyConfig("h", 0, null, null).isValid()).isFalse();
        assertThat(new ProxyConfig("h", 65536, null, null).isValid()).isFalse();
        assertThat(new ProxyConfig(null, 80, null, null).isValid()).isFalse();
    }

    @Test
    void theJavaProxyIsHttpWithTheHostAndThePortOrTheDefaultOne() {
        Proxy withPort = new ProxyConfig("localhost", 3128, null, null).toJavaNetProxy();
        Proxy withoutPort = new ProxyConfig("localhost", null, null, null).toJavaNetProxy();

        assertThat(withPort.type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(((InetSocketAddress) withPort.address()).getPort()).isEqualTo(3128);
        assertThat(((InetSocketAddress) withoutPort.address()).getPort()).isEqualTo(80);
    }

    @Test
    void theBasicAuthenticationNeedsAUserAndAPassword() {
        assertThat(new ProxyConfig("h", 1, "bob", "secret".toCharArray()).basicAuthHeaderValue())
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("bob:secret".getBytes()));
        assertThat(new ProxyConfig("h", 1, null, "secret".toCharArray()).basicAuthHeaderValue()).isNull();
        assertThat(new ProxyConfig("h", 1, "bob", null).basicAuthHeaderValue()).isNull();
        assertThat(new ProxyConfig("h", 1, "bob", new char[0]).basicAuthHeaderValue()).isNull();
    }
}
