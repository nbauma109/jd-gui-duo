/*******************************************************************************
 * Copyright (C) 2022 GPLv3
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
package org.jd.gui.util.matcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArtifactVersionMatcherTest {

    private ArtifactVersionMatcher artifactVersionMatcher;

    @Test
    public void testArtifactVersion() throws Exception {
        checkArtifactVersion("xbean-spring-3.11.1", "xbean-spring", "3.11.1");
        checkArtifactVersion("adapter-rxjava2-2.7.1", "adapter-rxjava2", "2.7.1");
        checkArtifactVersion("antlr4-runtime-4.5.3", "antlr4-runtime", "4.5.3");
        checkArtifactVersion("ant-1.10.5", "ant", "1.10.5");
        checkArtifactVersion("batik-i18n-1.14", "batik-i18n", "1.14");
        checkArtifactVersion("xmlparser-0.5-beta", "xmlparser", "0.5-beta");
        checkArtifactVersion("spring-security-kerberos-client-1.0.1.RELEASE", "spring-security-kerberos-client", "1.0.1.RELEASE");
        checkArtifactVersion("mvel2-2.4.4.Final", "mvel2", "2.4.4.Final");
        checkArtifactVersion("kie-soup-project-datamodel-commons-7.43.1.Final", "kie-soup-project-datamodel-commons", "7.43.1.Final");
        checkArtifactVersion("log4j-slf4j-impl-2.13.3", "log4j-slf4j-impl", "2.13.3");
        checkArtifactVersion("json_simple-r1", "json_simple", "r1");
        checkArtifactVersion("jmxremote_optional-1.0_01-ea", "jmxremote_optional", "1.0_01-ea");
        checkArtifactVersion("jtidy-r938", "jtidy", "r938");
        checkArtifactVersion("jsr305-3.0.2", "jsr305", "3.0.2");
        checkArtifactVersion("javassist-3.27.0-GA", "javassist", "3.27.0-GA");
        checkArtifactVersion("jboss-msc-1.0.4.GA", "jboss-msc", "1.0.4.GA");
        checkArtifactVersion("javacup-0.10k", "javacup", "0.10k");
        checkArtifactVersion("jaxb1-impl-2.2", "jaxb1-impl", "2.2");
        checkArtifactVersion("jackson-datatype-jsr310-2.11.4", "jackson-datatype-jsr310", "2.11.4");
        checkArtifactVersion("hibernate-jpa-2.1-api-1.0.0.Final", "hibernate-jpa-2.1-api", "1.0.0.Final");
        checkArtifactVersion("guava-30.1-jre", "guava", "30.1-jre");
        checkArtifactVersion("geronimo-j2ee-management_1.1_spec-1.0.1", "geronimo-j2ee-management_1.1_spec", "1.0.1");
        checkArtifactVersion("dom4j-2.1.3", "dom4j", "2.1.3");
        checkArtifactVersion("core-renderer-R8", "core-renderer", "R8");
        checkArtifactVersion("commons-math3-3.6.1", "commons-math3", "3.6.1");
        checkArtifactVersion("com.springsource.org.hamcrest.core-1.1.0", "com.springsource.org.hamcrest.core", "1.1.0");
        checkArtifactVersion("cglib-nodep-2.1_3", "cglib-nodep", "2.1_3");
        checkArtifactVersion("proguard-gradle-7.2.0-beta6", "proguard-gradle", "7.2.0-beta6");
        checkArtifactVersion("xpp3_min-1.1.4c", "xpp3_min", "1.1.4c");
        checkArtifactVersion("test-1.0-SNAPSHOT", "test", "1.0-SNAPSHOT");
        checkArtifactVersion("test-1.0.0-10.1.0.0", "test", "1.0.0-10.1.0.0");
        checkArtifactVersion("jboss-client", "jboss-client", "");
    }

    private void checkArtifactVersion(String baseFileName, String expectedArtifactId, String expectedVersion) {
        artifactVersionMatcher = new ArtifactVersionMatcher();
        artifactVersionMatcher.parse(baseFileName);
        assertEquals(expectedVersion, artifactVersionMatcher.getVersion());
        assertEquals(expectedArtifactId, artifactVersionMatcher.getArtifactId());
    }
}
