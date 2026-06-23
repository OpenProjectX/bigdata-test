package org.openprojectx.bigdata.test.extensions.core

import java.nio.file.Files
import java.nio.file.Path

class BigDataExtensionResourceLoader(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    private val resourceDirectories: Iterable<Path> = emptyList(),
) {
    fun readText(location: String): String = when {
        location.startsWith("classpath:") -> {
            val resource = location.removePrefix("classpath:").trimStart('/')
            classLoader.getResource(resource)?.readText()
                ?: resourceDirectories
                    .asSequence()
                    .map { it.resolve(resource) }
                    .firstOrNull(Files::isRegularFile)
                    ?.let(Files::readString)
                ?: error(
                    "Classpath resource '$resource' was not found in the extension runtime classpath " +
                        "or configured resource directories: ${resourceDirectories.joinToString()}",
                )
        }
        location.startsWith("file:") -> Files.readString(Path.of(location.removePrefix("file:")))
        else -> location
    }
}
