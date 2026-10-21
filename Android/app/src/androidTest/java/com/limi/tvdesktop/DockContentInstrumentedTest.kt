package com.limi.tvdesktop

import android.content.Intent
import android.database.MatrixCursor
import org.junit.Assert.*
import org.junit.Test

class DockContentInstrumentedTest {
    @Test fun readsOnlyVisibleProgramsOwnedByFocusedApp() {
        val columns = arrayOf("_id", "package_name", "title", "short_description", "poster_art_uri", "intent_uri", "browsable")
        MatrixCursor(columns).use { cursor ->
            cursor.addRow(arrayOf("1", "tv.example", "音乐歌单", "今日推荐", "https://example.test/art.jpg", "https://example.test/playlist", "1"))
            cursor.addRow(arrayOf("2", "tv.other", "其他应用", "", "", "https://example.test/other", "1"))
            cursor.addRow(arrayOf("3", "tv.example", "已隐藏", "", "", "https://example.test/hidden", "0"))
            cursor.moveToFirst()
            val program = DockContent.readProgram(cursor, "preview_program", "应用推荐", "tv.example")!!
            assertEquals("音乐歌单", program.title)
            assertEquals("tv.example", program.packageName)
            cursor.moveToNext()
            assertNull(DockContent.readProgram(cursor, "preview_program", "应用推荐", "tv.example"))
            cursor.moveToNext()
            assertNull(DockContent.readProgram(cursor, "preview_program", "应用推荐", "tv.example"))
        }
    }

    @Test fun contentIntentIsRestrictedToOwnerAndSafeActions() {
        val valid = DockContent.safeIntent("https://example.test/album", "tv.example")!!
        assertEquals(Intent.ACTION_VIEW, valid.action)
        assertEquals("tv.example", valid.`package`)
        assertNull(DockContent.safeIntent("intent:#Intent;action=android.intent.action.DELETE;end", "tv.example"))
        assertNull(DockContent.safeIntent("intent:#Intent;action=android.intent.action.VIEW;component=tv.other/.Main;end", "tv.example"))
    }
}
