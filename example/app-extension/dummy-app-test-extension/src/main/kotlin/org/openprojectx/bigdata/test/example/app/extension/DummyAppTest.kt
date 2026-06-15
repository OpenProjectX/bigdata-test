package org.openprojectx.bigdata.test.example.app.extension

import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(DummyAppTestExtension::class)
annotation class DummyAppTest(
    val autoStart: Boolean = true,
    val autoStop: Boolean = true,
    val customizer: KClass<out DummyAppConfigCustomizer> = NoopDummyAppConfigCustomizer::class,
)
