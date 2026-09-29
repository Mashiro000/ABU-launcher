package com.limi.tvdesktop.plugins

import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginScriptManifestValidatorTest {
    private fun valid() = JSONObject("""{
      "schemaVersion":1,"id":"com.example.demo","version":"1.0.0","name":"Demo","author":"Dev",
      "kind":"ui","entry":"dist/index.js","hostApi":">=1.1.0 <2.0.0",
      "surfaces":["home"],"slots":["home.quickActions"],
      "permissions":[{"id":"network","title":"Fetch"}],"networkDomains":["api.example.com"],
      "services":[{"name":"library","version":1}]
    }""")

    @Test fun acceptsMatchingContract() { PluginScriptManifestValidator.validate(valid()) }

    @Test fun rejectsUnsupportedSurface() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginScriptManifestValidator.validate(valid().put("surfaces", org.json.JSONArray("[\"account\"]")))
        }
    }

    @Test fun rejectsPathTraversalAndUndeclaredNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginScriptManifestValidator.validate(valid().put("entry", "../entry.js"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginScriptManifestValidator.validate(valid().put("permissions", org.json.JSONArray()))
        }
    }

    @Test fun rejectsDuplicateServicesAndFutureHostApi() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginScriptManifestValidator.validate(valid().put("services", org.json.JSONArray("[{\"name\":\"x\",\"version\":1},{\"name\":\"x\",\"version\":2}]")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginScriptManifestValidator.validate(valid().put("hostApi", ">=1.2.0 <2.0.0"))
        }
    }
}
