package com.limi.tvdesktop.plugins.runtime

internal object PluginRuntimeProtocol {
    const val EXECUTE = 1
    const val RESULT = 2
    const val KEY_REQUEST_ID = "requestId"
    const val KEY_PLUGIN_ID = "pluginId"
    const val KEY_SOURCE = "source"
    const val KEY_METHOD = "method"
    const val KEY_INPUT = "input"
    const val KEY_RESULT = "result"
    const val KEY_ERROR = "error"
    const val MAX_RESULT_CHARS = 1024 * 1024
}
