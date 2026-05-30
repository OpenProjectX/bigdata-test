package org.openprojectx.bigdata.test.extensions.junit5

import org.junit.jupiter.api.extension.ExtendWith
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.config.NoopBigDataExtensionsConfigurer
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(BigDataExtensionsExtension::class)
annotation class BigDataExtensions(
    vararg val value: String,
    val configurer: KClass<out BigDataExtensionsConfigurer> = NoopBigDataExtensionsConfigurer::class,
)
