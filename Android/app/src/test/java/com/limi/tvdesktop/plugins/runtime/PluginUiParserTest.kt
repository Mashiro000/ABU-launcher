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

    @Test fun parsesFormNavigationAndServiceValue() {
        val output = PluginUiParser.parse("""{"ui":{"type":"list","children":[{"type":"input","id":"query","hint":"Search"},{"type":"toggle","id":"enabled","value":"true"},{"type":"progress","progress":0.5}]},"navigation":{"push":"settings/account"},"value":{"ok":true}}""")
        assertEquals("input", output.ui?.children?.first()?.type)
        assertEquals("settings/account", output.navigation?.push)
        assertEquals(true, output.value?.getBoolean("ok"))
    }
}
