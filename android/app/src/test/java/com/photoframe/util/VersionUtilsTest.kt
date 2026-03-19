package com.photoframe.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VersionUtilsTest {
    @Test fun `newer major version`()      { assertTrue(isNewerVersion("2.0.0", "1.0.0")) }
    @Test fun `newer minor version`()      { assertTrue(isNewerVersion("1.1.0", "1.0.0")) }
    @Test fun `newer patch version`()      { assertTrue(isNewerVersion("1.0.1", "1.0.0")) }
    @Test fun `same version`()             { assertFalse(isNewerVersion("1.0.0", "1.0.0")) }
    @Test fun `older version`()            { assertFalse(isNewerVersion("1.0.0", "2.0.0")) }
    @Test fun `different length versions`() { assertTrue(isNewerVersion("1.0.0.1", "1.0.0")) }
}
