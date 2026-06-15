package org.openprojectx.bigdata.test.example.app.framework

class DummyApp(
    val config: DummyAppConfig,
) {
    var started: Boolean = false
        private set

    fun start() {
        started = true
    }

    fun stop() {
        started = false
    }

    fun property(name: String): String? =
        config.properties[name]
}
