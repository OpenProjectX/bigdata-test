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

    fun readBytes(location: String): ByteArray = when {
        location.startsWith("classpath:") -> {
            val resource = location.removePrefix("classpath:").trimStart('/')
            classLoader.getResource(resource)?.readBytes()
                ?: resourceDirectories
                    .asSequence()
                    .map { it.resolve(resource) }
                    .firstOrNull(Files::isRegularFile)
                    ?.let(Files::readAllBytes)
                ?: error(
                    "Classpath resource '$resource' was not found in the extension runtime classpath " +
                        "or configured resource directories: ${resourceDirectories.joinToString()}",
                )
        }
        location.startsWith("file:") -> Files.readAllBytes(Path.of(location.removePrefix("file:")))
        else -> Files.readAllBytes(Path.of(location))
    }

    fun resolveDirectory(location: String): Path {
        val path = when {
            location.startsWith("file:") -> Path.of(location.removePrefix("file:"))
            location.startsWith("classpath:") -> {
                val resource = location.removePrefix("classpath:").trimStart('/')
                resourceDirectories
                    .asSequence()
                    .map { it.resolve(resource) }
                    .firstOrNull(Files::isDirectory)
                    ?: error(
                        "Classpath directory '$resource' cannot be resolved from the extension resource directories. " +
                            "Use a file path for directory uploads when resources are packaged in a jar.",
                    )
            }
            else -> Path.of(location)
        }
        require(Files.isDirectory(path)) { "Upload source directory '$location' does not exist or is not a directory" }
        return path
    }
}
