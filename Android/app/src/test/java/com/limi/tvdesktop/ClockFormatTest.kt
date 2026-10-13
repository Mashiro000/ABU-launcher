package com.limi.tvdesktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ClockFormatTest {
    private fun display(input: String, format: DesktopPreferences.TimeFormat, seconds: Boolean): String {
        val source = SimpleDateFormat("HH:mm:ss", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val target = SimpleDateFormat(DesktopPreferences.ClockDisplay.pattern(format, seconds), Locale.CHINA)
            .apply { timeZone = source.timeZone }
        return target.format(source.parse(input)!!)
    }

    @Test fun twelveHourClockDistinguishesMidnightAndNoon() {
        assertEquals("上午 12:05:09", display("00:05:09", DesktopPreferences.TimeFormat.HOUR_12, true))
        assertEquals("下午 12:05:09", display("12:05:09", DesktopPreferences.TimeFormat.HOUR_12, true))
    }

    @Test fun twentyFourHourClockAndSecondsToggle() {
        assertEquals("23:05:09", display("23:05:09", DesktopPreferences.TimeFormat.HOUR_24, true))
        assertEquals("23:05", display("23:05:09", DesktopPreferences.TimeFormat.HOUR_24, false))
        assertEquals("下午 11:05", display("23:05:09", DesktopPreferences.TimeFormat.HOUR_12, false))
    }
}
