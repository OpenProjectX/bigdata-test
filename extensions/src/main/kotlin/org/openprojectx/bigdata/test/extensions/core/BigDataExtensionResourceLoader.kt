package org.openprojectx.bigdata.test.extensions.core

import java.nio.file.Files
import java.nio.file.Path

class BigDataExtensionResourceLoader(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) {
    fun readText(location: String): String = when {
        location.startsWith("classpath:") -> {
            val resource = location.removePrefix("classpath:").trimStart('/')
            classLoader.getResource(resource)?.readText()
                ?: error("Classpath resource '$resource' was not found")
        }
        location.startsWith("file:") -> Files.readString(Path.of(location.removePrefix("file:")))
        else -> location
    }
}
