package com.limi.tvdesktop.plugins

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHostApiTest {
    @Test fun acceptsCompatibleRange() {
        assertTrue(PluginHostApi.supports(">=1.0.0 <2.0.0"))
        assertTrue(PluginHostApi.supports(">=1.1.0 <2.0.0"))
        assertFalse(PluginHostApi.supports(">=1.2.0 <2.0.0"))
        assertFalse(PluginHostApi.supports(">=0.9.0 <1.0.0"))
        assertFalse(PluginHostApi.supports("anything"))
    }
}
