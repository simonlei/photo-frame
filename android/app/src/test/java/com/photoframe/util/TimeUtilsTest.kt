package com.photoframe.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TimeUtilsTest {
    @Test fun `same day period - inside`()       { assertTrue(isInNightPeriod(10, 0, 8, 0, 22, 0)) }
    @Test fun `same day period - outside`()      { assertFalse(isInNightPeriod(23, 0, 8, 0, 22, 0)) }
    @Test fun `cross midnight - night side`()    { assertTrue(isInNightPeriod(23, 0, 22, 0, 8, 0)) }
    @Test fun `cross midnight - day side`()      { assertFalse(isInNightPeriod(12, 0, 22, 0, 8, 0)) }
    @Test fun `cross midnight - early morning`() { assertTrue(isInNightPeriod(3, 0, 22, 0, 8, 0)) }
    @Test fun `exact start boundary`()           { assertTrue(isInNightPeriod(22, 0, 22, 0, 8, 0)) }
    @Test fun `exact end boundary`()             { assertFalse(isInNightPeriod(8, 0, 22, 0, 8, 0)) }
}
