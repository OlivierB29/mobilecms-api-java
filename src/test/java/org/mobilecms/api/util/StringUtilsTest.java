package org.mobilecms.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Test
    void slugifyStripsAccentsAndPunctuation() {
        assertEquals("ete-kendo-2026", StringUtils.slugify("Été kendo 2026"));
    }

    @Test
    void strnatcmpOrdersNumericSuffixes() {
        assertEquals(-1, Integer.signum(StringUtils.strnatcmp("file2", "file10")));
        assertEquals(1, Integer.signum(StringUtils.strnatcmp("file10", "file2")));
    }
}
