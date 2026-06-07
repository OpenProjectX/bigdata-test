package org.openprojectx.bigdata.test.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

class BigDataTestGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "bigDataTest",
            BigDataTestGradleExtension::class.java,
        )
        extension.localStackS3.image.convention("localstack/localstack:4.14.0")
        extension.fakeGcs.image.convention("fsouza/fake-gcs-server:1.54")

        val service = project.gradle.sharedServices.registerIfAbsent(
            "${project.path}:bigDataTestKit",
            BigDataTestGradleService::class.java,
        ) { spec ->
            spec.maxParallelUsages.set(1)
            spec.parameters.enabled.set(extension.enabled)
            spec.parameters.injectRawEndpointProperties.set(extension.injectRawEndpointProperties)
            spec.parameters.injectNamespacedEndpointProperties.set(extension.injectNamespacedEndpointProperties)
            spec.parameters.injectEnvironmentVariables.set(extension.injectEnvironmentVariables)
            spec.parameters.extensionConfig.set(
                project.provider {
                    extension.extensionConfig.get().map { location ->
                        project.resolveExtensionConfigLocation(location)
                    }
                },
            )
            spec.parameters.containerLogLevels.set(extension.containerLogLevels)

            spec.parameters.kerberos.set(extension.services.kerberos)
            spec.parameters.hdfs.set(extension.services.hdfs)
            spec.parameters.hiveMetastore.set(extension.services.hiveMetastore)
            spec.parameters.clouderaHms.set(extension.services.clouderaHms)
            spec.parameters.kafka.set(extension.services.kafka)
            spec.parameters.schemaRegistry.set(extension.services.schemaRegistry)
            spec.parameters.kafkaUi.set(extension.services.kafkaUi)
            spec.parameters.localStackS3.set(extension.services.localStackS3)
            spec.parameters.fakeGcs.set(extension.services.fakeGcs)

            spec.parameters.sameHostPorts.set(extension.ports.sameHostPorts)
            spec.parameters.kerberosKdcPort.set(extension.ports.kerberosKdc)
            spec.parameters.hdfsNameNodePort.set(extension.ports.hdfsNameNode)
            spec.parameters.hdfsDataNodePort.set(extension.ports.hdfsDataNode)
            spec.parameters.hdfsWebPort.set(extension.ports.hdfsWeb)
            spec.parameters.hiveMetastorePort.set(extension.ports.hiveMetastore)
            spec.parameters.kafkaPort.set(extension.ports.kafka)
            spec.parameters.schemaRegistryPort.set(extension.ports.schemaRegistry)
            spec.parameters.kafkaUiPort.set(extension.ports.kafkaUi)
            spec.parameters.localStackS3Port.set(extension.ports.localStackS3)
            spec.parameters.fakeGcsPort.set(extension.ports.fakeGcs)

            spec.parameters.kerberosImage.set(extension.kerberos.image)
            spec.parameters.kerberosRealm.set(extension.kerberos.realm)
            spec.parameters.kerberosDomain.set(extension.kerberos.domain)
            spec.parameters.kerberosClientPrincipal.set(extension.kerberos.clientPrincipal)
            spec.parameters.kerberosClientPassword.set(extension.kerberos.clientPassword)
            spec.parameters.kerberosStartupTimeoutSeconds.set(extension.kerberos.startupTimeoutSeconds)
            spec.parameters.kerberosMaterialTimeoutSeconds.set(extension.kerberos.materialTimeoutSeconds)
            spec.parameters.kerberosAdminAttempts.set(extension.kerberos.adminAttempts)
            spec.parameters.kerberosAdminRetryDelaySeconds.set(extension.kerberos.adminRetryDelaySeconds)
            spec.parameters.kerberosDebug.set(extension.kerberos.debug)

            spec.parameters.tlsEnabled.set(extension.tls.enabled)
            spec.parameters.tlsCaCertPath.set(extension.tls.caCertPath)
            spec.parameters.tlsCaKeyPath.set(extension.tls.caKeyPath)
            spec.parameters.tlsTrustStorePath.set(extension.tls.trustStorePath)
            spec.parameters.tlsTrustStorePassword.set(extension.tls.trustStorePassword)
            spec.parameters.tlsHaproxyImage.set(extension.tls.haproxyImage)

            spec.parameters.hdfsImage.set(extension.hdfs.image)
            spec.parameters.hdfsKerberosEnabled.set(extension.hdfs.kerberosEnabled)
            spec.parameters.hdfsDataNodeHostname.set(extension.hdfs.dataNodeHostname)

            spec.parameters.hiveMetastoreImage.set(extension.hiveMetastore.image)
            spec.parameters.hiveMetastoreDatabaseImage.set(extension.hiveMetastore.databaseImage)
            spec.parameters.hiveMetastoreDatabaseName.set(extension.hiveMetastore.databaseName)
            spec.parameters.hiveMetastoreDatabaseUser.set(extension.hiveMetastore.databaseUser)
            spec.parameters.hiveMetastoreDatabasePassword.set(extension.hiveMetastore.databasePassword)
            spec.parameters.hiveMetastoreWarehouseDir.set(extension.hiveMetastore.warehouseDir)
            spec.parameters.hiveMetastoreKerberosEnabled.set(extension.hiveMetastore.kerberosEnabled)

            spec.parameters.clouderaHmsImage.set(extension.clouderaHms.image)
            spec.parameters.clouderaHmsWarehouseDir.set(extension.clouderaHms.warehouseDir)
            spec.parameters.clouderaHmsKerberosEnabled.set(extension.clouderaHms.kerberosEnabled)

            spec.parameters.kafkaImage.set(extension.kafka.image)
            spec.parameters.schemaRegistryImage.set(extension.kafka.schemaRegistryImage)
            spec.parameters.kafkaUiImage.set(extension.kafka.kafkaUiImage)
            spec.parameters.kafkaKerberosEnabled.set(extension.kafka.kerberosEnabled)
            spec.parameters.kafkaUiKerberosEnabled.set(extension.kafka.kafkaUiKerberosEnabled)

            spec.parameters.localStackS3Image.set(extension.localStackS3.image)
            spec.parameters.fakeGcsImage.set(extension.fakeGcs.image)

            spec.parameters.containerLogMode.set(extension.containerLogs.mode)
            spec.parameters.containerLogDirectory.set(extension.containerLogs.directory)
        }

        val startTask = project.tasks.register("bigDataTestStart", BigDataTestStartTask::class.java) { task ->
            task.group = "bigdata-test"
            task.description = "Start configured bigdata-test containers in the Gradle daemon."
            task.kitService.set(service)
            task.usesService(service)
        }
        project.tasks.register("bigDataTestStop", BigDataTestStopTask::class.java) { task ->
            task.group = "bigdata-test"
            task.description = "Stop the bigdata-test containers owned by the Gradle daemon."
            task.kitService.set(service)
            task.usesService(service)
        }

        project.afterEvaluate {
            if (extension.enabled.get() && extension.autoConfigureJavaExecTasks.get()) {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    if (task.name == "bigDataTestStart" || task.name == "bigDataTestStop") return@configureEach
                    task.dependsOn(startTask)
                    task.usesService(service)
                    task.doFirst {
                        val kitService = service.get()
                        val properties = kitService.startIfNeeded()
                        properties.forEach { (key, value) -> task.systemProperty(key, value) }
                        task.environment(kitService.injectedEnvironmentVariables(properties))
                    }
                }
            }

            if (extension.enabled.get() && extension.autoConfigureTestTasks.get()) {
                project.tasks.withType(Test::class.java).configureEach { task ->
                    task.dependsOn(startTask)
                    task.usesService(service)
                    task.doFirst {
                        val kitService = service.get()
                        val properties = kitService.startIfNeeded()
                        properties.forEach { (key, value) -> task.systemProperty(key, value) }
                        task.environment(kitService.injectedEnvironmentVariables(properties))
                    }
                }
            }
        }
    }

    private fun Project.resolveExtensionConfigLocation(location: String): String {
        if (!location.startsWith("classpath:")) return location
        val resource = location.removePrefix("classpath:").trimStart('/')
        val sourceSets = extensions.findByName("sourceSets") as? SourceSetContainer
        val resourceFile = sourceSets
            ?.findByName("main")
            ?.resources
            ?.srcDirs
            ?.asSequence()
            ?.map { it.resolve(resource) }
            ?.firstOrNull { it.isFile }
        return resourceFile?.let { "file:${it.absolutePath}" } ?: location
    }
}
