package org.jd.gui.util.parser.jdt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.Assert.assertEquals;

public class ASTParserFactoryTest {
    @ParameterizedTest
    @CsvSource({
            "1.8.0_202,1.8",
            "1.8.0_202-b08,1.8",
            "1.7.0_80,1.7",
            "1.6.0_52,1.6",
            "1.5.0_06,1.5",
            "9.0.4,9",
            "10.0.2,10",
            "11.0.17,11",
            "17.0.9,17",
            "17.0.6+10,17",
            "21.0.1+12,21",
            "25.0.1,25",
            "25-ea+5,25",
            "25+1,25"
    })
    void testResolveJDKVersionBuildJdkManifestValues(String buildJdk, String expected) {
        assertEquals(expected, ASTParserFactory.resolveJDKVersion(buildJdk));
    }

    @Test
    void testResolveJDKVersionAllReleasesNineThroughTwentyFive() {
        for (int major = 9; major <= 25; major++) {
            assertEquals(Integer.toString(major), ASTParserFactory.resolveJDKVersion(major + ".0.1"));
            assertEquals(Integer.toString(major), ASTParserFactory.resolveJDKVersion(major + ".0.1+7"));
        }
    }

    @Test
    void testResolveJDKVersionFutureReleases() {
        int[] futureMajors = { 26, 30 };
        for (int major : futureMajors) {
            assertEquals(Integer.toString(major), ASTParserFactory.resolveJDKVersion(major + ".0.0"));
            assertEquals(Integer.toString(major), ASTParserFactory.resolveJDKVersion(major + ".0.0+1"));
            assertEquals(Integer.toString(major), ASTParserFactory.resolveJDKVersion(major + "-ea+1"));
        }
    }

    @Test
    void testResolveJDKVersionLegacyOneDotReleasesOneThroughEight() {
        for (int major = 1; major <= 8; major++) {
            assertEquals("1." + major, ASTParserFactory.resolveJDKVersion("1." + major + ".0"));
            assertEquals("1." + major, ASTParserFactory.resolveJDKVersion("1." + major + ".0_1"));
        }
    }
}
