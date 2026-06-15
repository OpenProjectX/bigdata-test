package org.openprojectx.bigdata.test.example.app.framework

object DummyAppFactory {
    @JvmStatic
    fun create(config: DummyAppConfig): DummyApp =
        DummyApp(config)
}
