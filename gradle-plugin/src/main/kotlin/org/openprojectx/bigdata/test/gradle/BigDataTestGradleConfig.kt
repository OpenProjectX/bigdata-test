package org.openprojectx.bigdata.test.gradle

import java.util.Base64
import org.openprojectx.bigdata.test.core.config.BigDataTestConfig
import org.openprojectx.bigdata.test.core.config.BigDataTestConfigLoader

internal typealias BigDataTestGradleConfig = BigDataTestConfig
internal typealias BigDataTestGradleConfigLoader = BigDataTestConfigLoader

internal fun BigDataTestConfig.encodedContainerCustomizations(): List<String> =
    containerCustomizations.flatMap { (service, customization) ->
        buildList {
            customization.networkMode?.let { add(encode("network", service.name, it)) }
            customization.environment.forEach { (name, value) -> add(encode("env", service.name, name, value)) }
            customization.files.forEach { file ->
                add(
                    encode(
                        "file",
                        service.name,
                        file.containerPath,
                        file.hostPath.orEmpty(),
                        file.content?.let { Base64.getEncoder().encodeToString(it) }.orEmpty(),
                        file.fileMode?.toString().orEmpty(),
                    ),
                )
            }
            customization.mounts.forEach { mount ->
                add(encode("mount", service.name, mount.hostPath, mount.containerPath, mount.readOnly.toString()))
            }
            customization.ports.forEach { port ->
                add(encode("port", service.name, port.containerPort.toString(), port.hostPort.toString()))
            }
        }
    }

internal fun encode(vararg parts: String): String =
    parts.joinToString("|") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }

internal fun decode(encoded: String): List<String> =
    encoded.split('|').map { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
