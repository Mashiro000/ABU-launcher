package com.limi.tvdesktop.plugins.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginUiParserTest {
    @Test fun parsesDeclarativeTreeAndCapabilities() {
        val output = PluginUiParser.parse("""{"ui":{"type":"column","children":[{"type":"text","text":"Hello"},{"type":"button","text":"Go","action":"open"}]},"capabilities":[{"id":"one","capability":"device.info","arguments":{}}]}""")
        assertEquals("column", output.ui?.type)
        assertEquals("Go", output.ui?.children?.get(1)?.text)
        assertEquals("device.info", output.capabilities.single().capability)
    }

    @Test fun rejectsExcessiveDepth() {
        var node = "{\"type\":\"text\"}"
        repeat(14) { node = "{\"type\":\"column\",\"children\":[$node]}" }
        assertThrows(IllegalArgumentException::class.java) { PluginUiParser.parse("{\"ui\":$node}") }
    }
}
