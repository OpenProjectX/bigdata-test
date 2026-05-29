package org.openprojectx.bigdata.test.extensions.junit5

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(BigDataExtensionsExtension::class)
annotation class BigDataExtensions(
    vararg val value: String,
)
