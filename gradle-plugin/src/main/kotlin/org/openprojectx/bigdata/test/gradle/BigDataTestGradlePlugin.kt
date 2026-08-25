package org.openprojectx.bigdata.test.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.openprojectx.bigdata.test.core.ClouderaHmsDatabaseType
import org.openprojectx.bigdata.test.core.DEFAULT_FAKE_GCS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_ICEBERG_REST_CATALOG_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_S3_IMAGE
import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType

class BigDataTestGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "bigDataTest",
            BigDataTestGradleExtension::class.java,
        )
        extension.s3.image.convention(DEFAULT_S3_IMAGE)
        extension.fakeGcs.image.convention(DEFAULT_FAKE_GCS_IMAGE)
        extension.icebergRestCatalog.image.convention(DEFAULT_ICEBERG_REST_CATALOG_IMAGE)
        extension.extensionRuntime.extensionsVersion.convention(pluginImplementationVersion())
        val extensionRuntimeClasspath = project.configurations.create("bigDataTestExtensionRuntime") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = "Runtime classpath used by the bigdata-test Gradle plugin to execute extensions."
        }

        val service = project.gradle.sharedServices.registerIfAbsent(
            "${project.path}:bigDataTestKit",
            BigDataTestGradleService::class.java,
        ) { spec ->
            spec.maxParallelUsages.set(1)
            spec.parameters.projectDirectory.set(project.layout.projectDirectory.asFile.absolutePath)
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
            spec.parameters.extensionResourceDirectories.set(
                project.provider { project.extensionResourceDirectories() },
            )
            spec.parameters.extensionRuntimeClasspath.from(extensionRuntimeClasspath)
            spec.parameters.containerLogLevels.set(extension.containerLogLevels)
            spec.parameters.containerCustomizations.set(extension.containerCustomizations)
            spec.parameters.configLocations.set(
                project.provider { extension.config.get().map { project.resolveExtensionConfigLocation(it) } },
            )
            spec.parameters.instances.set(
                project.provider {
                    extension.instances.get().mapValues { (_, location) -> project.resolveExtensionConfigLocation(location) }
                },
            )

            spec.parameters.kerberos.set(extension.services.kerberos)
            spec.parameters.hdfs.set(extension.services.hdfs)
            spec.parameters.hiveMetastore.set(extension.services.hiveMetastore)
            spec.parameters.clouderaHms.set(extension.services.clouderaHms)
            spec.parameters.kafka.set(extension.services.kafka)
            spec.parameters.schemaRegistry.set(extension.services.schemaRegistry)
            spec.parameters.kafkaUi.set(extension.services.kafkaUi)
            spec.parameters.s3.set(extension.services.s3)
            spec.parameters.fakeGcs.set(extension.services.fakeGcs)
            spec.parameters.icebergRestCatalog.set(extension.services.icebergRestCatalog)

            spec.parameters.sameHostPorts.set(extension.ports.sameHostPorts)
            spec.parameters.kerberosKdcPort.set(extension.ports.kerberosKdc)
            spec.parameters.hdfsNameNodePort.set(extension.ports.hdfsNameNode)
            spec.parameters.hdfsDataNodePort.set(extension.ports.hdfsDataNode)
            spec.parameters.hdfsWebPort.set(extension.ports.hdfsWeb)
            spec.parameters.hiveMetastorePort.set(extension.ports.hiveMetastore)
            spec.parameters.kafkaPort.set(extension.ports.kafka)
            spec.parameters.schemaRegistryPort.set(extension.ports.schemaRegistry)
            spec.parameters.kafkaUiPort.set(extension.ports.kafkaUi)
            spec.parameters.s3Port.set(extension.ports.s3)
            spec.parameters.fakeGcsPort.set(extension.ports.fakeGcs)
            spec.parameters.icebergRestCatalogPort.set(extension.ports.icebergRestCatalog)
            spec.parameters.icebergRestCatalogTlsPort.set(extension.ports.icebergRestCatalogTls)

            spec.parameters.kerberosImage.set(extension.kerberos.image)
            spec.parameters.kerberosRealm.set(extension.kerberos.realm)
            spec.parameters.kerberosDomain.set(extension.kerberos.domain)
            spec.parameters.kerberosClientPrincipal.set(extension.kerberos.clientPrincipal)
            spec.parameters.kerberosClientPassword.set(extension.kerberos.clientPassword)
            spec.parameters.kerberosMaterialDirectory.set(extension.kerberos.materialDirectory)
            spec.parameters.kerberosLocalKrb5ConfPath.set(extension.kerberos.localKrb5ConfPath)
            spec.parameters.kerberosLocalClientKeytabPath.set(extension.kerberos.localClientKeytabPath)
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
            spec.parameters.hdfsLocalHdfsSitePath.set(extension.hdfs.localHdfsSitePath)

            spec.parameters.hiveMetastoreImage.set(extension.hiveMetastore.image)
            spec.parameters.hiveMetastoreDatabaseType.set(extension.hiveMetastore.databaseType)
            spec.parameters.hiveMetastoreDatabaseImage.set(extension.hiveMetastore.databaseImage)
            spec.parameters.hiveMetastoreDatabaseName.set(extension.hiveMetastore.databaseName)
            spec.parameters.hiveMetastoreDatabaseUser.set(extension.hiveMetastore.databaseUser)
            spec.parameters.hiveMetastoreDatabasePassword.set(extension.hiveMetastore.databasePassword)
            spec.parameters.hiveMetastoreDatabaseHostPort.set(extension.hiveMetastore.databaseHostPort)
            spec.parameters.hiveMetastoreWarehouseDir.set(extension.hiveMetastore.warehouseDir)
            spec.parameters.hiveMetastoreLocalHiveSitePath.set(extension.hiveMetastore.localHiveSitePath)
            spec.parameters.hiveMetastoreLocalMetastoreSitePath.set(extension.hiveMetastore.localMetastoreSitePath)
            spec.parameters.hiveMetastoreKerberosEnabled.set(extension.hiveMetastore.kerberosEnabled)

            spec.parameters.clouderaHmsImage.set(extension.clouderaHms.image)
            spec.parameters.clouderaHmsDatabaseType.set(extension.clouderaHms.databaseType)
            spec.parameters.clouderaHmsDatabaseName.set(extension.clouderaHms.databaseName)
            spec.parameters.clouderaHmsDatabaseUser.set(extension.clouderaHms.databaseUser)
            spec.parameters.clouderaHmsDatabasePassword.set(extension.clouderaHms.databasePassword)
            spec.parameters.clouderaHmsDatabaseHostPort.set(extension.clouderaHms.databaseHostPort)
            spec.parameters.clouderaHmsWarehouseDir.set(extension.clouderaHms.warehouseDir)
            spec.parameters.clouderaHmsKerberosEnabled.set(extension.clouderaHms.kerberosEnabled)

            spec.parameters.kafkaImage.set(extension.kafka.image)
            spec.parameters.kafkaStartupTimeoutSeconds.set(extension.kafka.startupTimeoutSeconds)
            spec.parameters.schemaRegistryImage.set(extension.kafka.schemaRegistryImage)
            spec.parameters.kafkaUiImage.set(extension.kafka.kafkaUiImage)
            spec.parameters.kafkaKerberosEnabled.set(extension.kafka.kerberosEnabled)
            spec.parameters.kafkaUiKerberosEnabled.set(extension.kafka.kafkaUiKerberosEnabled)

            spec.parameters.s3Image.set(extension.s3.image)
            spec.parameters.fakeGcsImage.set(extension.fakeGcs.image)
            spec.parameters.icebergRestCatalogImage.set(extension.icebergRestCatalog.image)
            spec.parameters.icebergRestCatalogWarehouse.set(extension.icebergRestCatalog.warehouse)
            spec.parameters.icebergRestCatalogBackend.set(extension.icebergRestCatalog.catalogBackend)
            spec.parameters.icebergRestCatalogUri.set(extension.icebergRestCatalog.uri)
            spec.parameters.icebergRestCatalogJdbcDriver.set(extension.icebergRestCatalog.jdbcDriver)
            spec.parameters.icebergRestCatalogJdbcUser.set(extension.icebergRestCatalog.jdbcUser)
            spec.parameters.icebergRestCatalogJdbcPassword.set(extension.icebergRestCatalog.jdbcPassword)
            spec.parameters.icebergRestCatalogIoImpl.set(extension.icebergRestCatalog.ioImpl)
            spec.parameters.icebergRestCatalogTlsEnabled.set(extension.icebergRestCatalog.tlsEnabled)
            spec.parameters.icebergRestCatalogTlsDomain.set(extension.icebergRestCatalog.tlsDomain)

            spec.parameters.containerLogMode.set(extension.containerLogs.mode)
            spec.parameters.containerLogDirectory.set(extension.containerLogs.directory)
            spec.parameters.containerLogAppend.set(extension.containerLogs.append)
        }

        val startTask = project.tasks.register("bigDataTestStart", BigDataTestStartTask::class.java) { task ->
            task.group = "bigdata-test"
            task.description = "Start configured bigdata-test containers for this Gradle build."
            task.kitService.set(service)
            task.usesService(service)
        }
        project.tasks.register("bigDataTestRun", BigDataTestRunTask::class.java) { task ->
            task.group = "bigdata-test"
            task.description = "Start configured bigdata-test containers and keep them running until the Gradle process is interrupted."
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
            project.applyGradleTomlConfig(extension)
            project.configureExtensionRuntime(extension, extensionRuntimeClasspath)

            if (extension.enabled.get() && extension.autoConfigureJavaExecTasks.get()) {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    if (task.name == "bigDataTestStart" || task.name == "bigDataTestRun" || task.name == "bigDataTestStop") {
                        return@configureEach
                    }
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

    private fun Project.configureExtensionRuntime(
        extension: BigDataTestGradleExtension,
        configuration: Configuration,
    ) {
        if (!extension.extensionRuntime.enabled.get()) return

        val runtime = extension.extensionRuntime
        configuration.resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") { details ->
            details.select("at.yawk.lz4:lz4-java:${runtime.lz4Version.get()}")
            details.because("Kafka/Confluent and Spark publish different providers for the same lz4 capability.")
        }
        configuration.resolutionStrategy.eachDependency { details ->
            if (details.requested.group.startsWith("com.fasterxml.jackson")) {
                details.useVersion("2.15.2")
                details.because("The bigdata-test extension runtime executes Spark 3.5.x and keeps Jackson aligned independently from application dependency management.")
            }
        }
        val extensionVersion = runtime.extensionsVersion.get()
        if (runtime.useShadedArtifact.get()) {
            dependencies.add(configuration.name, "org.openprojectx.bigdata.test.core:extensions:$extensionVersion:runtime@jar")
            return
        }

        dependencies.add(configuration.name, "org.openprojectx.bigdata.test.core:extensions:$extensionVersion")
        val detected = if (runtime.autoDetect.get()) detectExtensionRuntimeNeeds(extension.extensionConfig.get()) else ExtensionRuntimeNeeds()
        val includeHadoop = runtime.includeHadoop.get() || detected.hadoop
        val includeKafkaAvro = runtime.includeKafkaAvro.get() || detected.kafkaAvro
        val includeSpark = runtime.includeSpark.get() || detected.spark

        if (includeHadoop || includeSpark) {
            dependencies.add(configuration.name, "org.apache.hadoop:hadoop-client-api:${runtime.hadoopVersion.get()}")
            dependencies.add(configuration.name, "org.apache.hadoop:hadoop-client-runtime:${runtime.hadoopVersion.get()}")
            val hadoopAws = dependencies.create(
                "org.apache.hadoop:hadoop-aws:${runtime.hadoopVersion.get()}",
            ) as ExternalModuleDependency
            hadoopAws.exclude(mapOf("group" to "software.amazon.awssdk", "module" to "bundle"))
            dependencies.add(configuration.name, hadoopAws)
            dependencies.add(configuration.name, "software.amazon.awssdk:s3:${runtime.awsSdkVersion.get()}")
            dependencies.add(configuration.name, "software.amazon.awssdk:s3-transfer-manager:${runtime.awsSdkVersion.get()}")
        }
        if (includeKafkaAvro) {
            dependencies.add(configuration.name, "io.confluent:kafka-avro-serializer:${runtime.confluentVersion.get()}")
            dependencies.add(configuration.name, "io.confluent:kafka-schema-registry-client:${runtime.confluentVersion.get()}")
            dependencies.add(configuration.name, "at.yawk.lz4:lz4-java:${runtime.lz4Version.get()}")
            dependencies.add(configuration.name, "org.apache.avro:avro:${runtime.avroVersion.get()}")
        }
        if (includeSpark) {
            dependencies.add(configuration.name, "org.apache.spark:spark-sql_2.12:${runtime.sparkVersion.get()}")
            dependencies.add(configuration.name, "org.apache.spark:spark-hive_2.12:${runtime.sparkVersion.get()}")
            dependencies.add(configuration.name, "org.apache.iceberg:iceberg-spark-3.5_2.12:${runtime.icebergVersion.get()}")
            dependencies.add(configuration.name, "org.apache.iceberg:iceberg-spark-extensions-3.5_2.12:${runtime.icebergVersion.get()}")
            dependencies.add(configuration.name, "org.apache.iceberg:iceberg-hive-metastore:${runtime.icebergVersion.get()}")
            dependencies.add(configuration.name, "org.apache.iceberg:iceberg-aws-bundle:${runtime.icebergVersion.get()}")
            dependencies.add(configuration.name, "javax.servlet:javax.servlet-api:4.0.1")
        }
    }

    private fun pluginImplementationVersion(): String =
        javaClass.`package`.implementationVersion?.takeIf { it.isNotBlank() } ?: "0.1.12-SNAPSHOT"

    private fun Project.extensionResourceDirectories(): List<String> {
        return buildList {
            resourceSourceSets()
                .flatMap { sourceSet ->
                    sourceSet.resources.srcDirs + listOfNotNull(sourceSet.output.resourcesDir)
                }
                .filter { it.isDirectory }
                .mapTo(this) { it.absolutePath }
            file("src/main/resources")
                .takeIf { it.isDirectory }
                ?.let { add(it.absolutePath) }
            file("src/test/resources")
                .takeIf { it.isDirectory }
                ?.let { add(it.absolutePath) }
        }.distinct()
    }

    private fun Project.detectExtensionRuntimeNeeds(locations: List<String>): ExtensionRuntimeNeeds {
        var needs = ExtensionRuntimeNeeds()
        locations.forEach { location ->
            val text = runCatching {
                val resolved = resolveExtensionConfigLocation(location)
                when {
                    resolved.startsWith("file:") -> file(resolved.removePrefix("file:")).readText()
                    resolved.startsWith("classpath:") -> {
                        val resource = resolved.removePrefix("classpath:").trimStart('/')
                        classpathResourceFile(resource)?.readText().orEmpty()
                    }
                    else -> file(resolved).readText()
                }
            }.getOrDefault("")
            needs = needs.copy(
                hadoop = needs.hadoop || text.contains("[s3Jceks]"),
                kafkaAvro = needs.kafkaAvro || text.contains("[kafkaAvro]"),
                spark = needs.spark || text.contains("sparkSqlPreparation") ||
                    text.contains("type = \"spark-sql-prep\"") ||
                    text.contains("type = \"spark-sql-preparation\""),
            )
        }
        return needs
    }

    private fun Project.applyGradleTomlConfig(extension: BigDataTestGradleExtension) {
        val locations = extension.config.get().map { resolveExtensionConfigLocation(it) }
        if (locations.isEmpty()) return

        val config = BigDataTestGradleConfigLoader(javaClass.classLoader).load(locations)
        extension.kerberos.image.tomlConvention(config.images.kerberos)
        extension.hdfs.image.tomlConvention(config.images.hdfs)
        extension.hiveMetastore.image.tomlConvention(config.images.hiveMetastore)
        extension.clouderaHms.databaseType.tomlConvention(config.clouderaHms.databaseType)
        extension.clouderaHms.image.tomlConvention(
            when (config.clouderaHms.databaseType) {
                ClouderaHmsDatabaseType.POSTGRESQL -> config.images.clouderaHms
                ClouderaHmsDatabaseType.MARIADB -> config.images.clouderaHmsMariadb
                null -> config.images.clouderaHms
            },
        )
        extension.clouderaHms.databaseName.tomlConvention(config.clouderaHms.databaseName)
        extension.clouderaHms.databaseUser.tomlConvention(config.clouderaHms.databaseUser)
        extension.clouderaHms.databasePassword.tomlConvention(config.clouderaHms.databasePassword)
        extension.clouderaHms.databaseHostPort.tomlConvention(config.clouderaHms.databaseHostPort)
        extension.hiveMetastore.databaseType.tomlConvention(config.hiveMetastore.databaseType)
        extension.hiveMetastore.databaseImage.tomlConvention(
            when (config.hiveMetastore.databaseType) {
                HiveMetastoreDatabaseType.POSTGRESQL -> config.images.hiveMetastorePostgres
                HiveMetastoreDatabaseType.MYSQL -> config.images.hiveMetastoreMysql
                null -> config.images.hiveMetastorePostgres
            },
        )
        extension.hiveMetastore.databaseHostPort.tomlConvention(config.hiveMetastore.databaseHostPort)
        extension.kafka.image.tomlConvention(config.images.kafka)
        extension.kafka.startupTimeoutSeconds.tomlConvention(config.kafka.startupTimeoutSeconds)
        extension.kafka.schemaRegistryImage.tomlConvention(config.images.schemaRegistry ?: config.kafka.schemaRegistryImage)
        extension.kafka.kafkaUiImage.tomlConvention(config.images.kafkaUi ?: config.kafka.kafkaUiImage)
        extension.s3.image.tomlConvention(config.images.s3)
        extension.fakeGcs.image.tomlConvention(config.images.fakeGcs)
        extension.icebergRestCatalog.image.tomlConvention(config.images.icebergRestCatalog)

        extension.services.kerberos.tomlConvention(config.services.kerberos)
        extension.services.hdfs.tomlConvention(config.services.hdfs ?: config.services.hdfsKerberos)
        extension.hdfs.kerberosEnabled.tomlConvention(config.services.hdfsKerberos)
        extension.services.hiveMetastore.tomlConvention(config.services.hiveMetastore)
        extension.services.clouderaHms.tomlConvention(config.services.clouderaHms)
        extension.hiveMetastore.kerberosEnabled.tomlConvention(config.services.hiveMetastoreKerberos)
        extension.clouderaHms.kerberosEnabled.tomlConvention(config.services.hiveMetastoreKerberos)
        extension.services.kafka.tomlConvention(config.services.kafka ?: config.services.kafkaKerberos)
        extension.kafka.kerberosEnabled.tomlConvention(config.services.kafkaKerberos)
        extension.services.schemaRegistry.tomlConvention(config.services.schemaRegistry)
        extension.services.kafkaUi.tomlConvention(config.services.kafkaUi)
        extension.kafka.kafkaUiKerberosEnabled.tomlConvention(config.services.kafkaUiKerberos)
        extension.services.s3.tomlConvention(config.services.s3)
        extension.services.fakeGcs.tomlConvention(config.services.fakeGcs)
        extension.services.icebergRestCatalog.tomlConvention(config.services.icebergRestCatalog)

        extension.kerberos.realm.tomlConvention(config.kerberos.realm)
        extension.kerberos.domain.tomlConvention(config.kerberos.domain)
        extension.kerberos.clientPrincipal.tomlConvention(config.kerberos.clientPrincipal)
        extension.kerberos.clientPassword.tomlConvention(config.kerberos.clientPassword)
        extension.kerberos.materialDirectory.tomlConvention(config.kerberos.materialDirectory)
        extension.kerberos.localKrb5ConfPath.tomlConvention(config.kerberos.localKrb5ConfPath)
        extension.kerberos.localClientKeytabPath.tomlConvention(config.kerberos.localClientKeytabPath)
        extension.kerberos.startupTimeoutSeconds.tomlConvention(config.kerberos.startupTimeoutSeconds)
        extension.kerberos.materialTimeoutSeconds.tomlConvention(config.kerberos.materialTimeoutSeconds)
        extension.kerberos.adminAttempts.tomlConvention(config.kerberos.adminAttempts)
        extension.kerberos.adminRetryDelaySeconds.tomlConvention(config.kerberos.adminRetryDelaySeconds)
        extension.kerberos.debug.tomlConvention(config.kerberos.debug)

        extension.tls.enabled.tomlConvention(config.tls.enabled)
        extension.tls.caCertPath.tomlConvention(config.tls.caCertPath)
        extension.tls.caKeyPath.tomlConvention(config.tls.caKeyPath)
        extension.tls.trustStorePath.tomlConvention(config.tls.trustStorePath)
        extension.tls.trustStorePassword.tomlConvention(config.tls.trustStorePassword)
        extension.tls.haproxyImage.tomlConvention(config.tls.haproxyImage)

        extension.hdfs.dataNodeHostname.tomlConvention(config.hdfs.dataNodeHostname)
        extension.hdfs.localHdfsSitePath.tomlConvention(config.hdfs.localHdfsSitePath)
        extension.hiveMetastore.databaseName.tomlConvention(config.hiveMetastore.databaseName)
        extension.hiveMetastore.databaseUser.tomlConvention(config.hiveMetastore.databaseUser)
        extension.hiveMetastore.databasePassword.tomlConvention(config.hiveMetastore.databasePassword)
        extension.hiveMetastore.warehouseDir.tomlConvention(config.hiveMetastore.warehouseDir)
        extension.hiveMetastore.localHiveSitePath.tomlConvention(config.hiveMetastore.localHiveSitePath)
        extension.hiveMetastore.localMetastoreSitePath.tomlConvention(config.hiveMetastore.localMetastoreSitePath)
        extension.clouderaHms.warehouseDir.tomlConvention(config.clouderaHms.warehouseDir)
        extension.icebergRestCatalog.warehouse.tomlConvention(config.icebergRestCatalog.warehouse)
        extension.icebergRestCatalog.catalogBackend.tomlConvention(config.icebergRestCatalog.catalogBackend)
        extension.icebergRestCatalog.uri.tomlConvention(config.icebergRestCatalog.uri)
        extension.icebergRestCatalog.jdbcDriver.tomlConvention(config.icebergRestCatalog.jdbcDriver)
        extension.icebergRestCatalog.jdbcUser.tomlConvention(config.icebergRestCatalog.jdbcUser)
        extension.icebergRestCatalog.jdbcPassword.tomlConvention(config.icebergRestCatalog.jdbcPassword)
        extension.icebergRestCatalog.ioImpl.tomlConvention(config.icebergRestCatalog.ioImpl)
        extension.icebergRestCatalog.tlsEnabled.tomlConvention(config.icebergRestCatalogTls.enabled)
        extension.icebergRestCatalog.tlsDomain.tomlConvention(config.icebergRestCatalogTls.domain)

        extension.ports.sameHostPorts.tomlConvention(config.ports.sameHostPorts)
        extension.ports.kerberosKdc.tomlConvention(config.ports.kerberosKdc)
        extension.ports.hdfsNameNode.tomlConvention(config.ports.hdfsNameNode)
        extension.ports.hdfsDataNode.tomlConvention(config.ports.hdfsDataNode)
        extension.ports.hdfsWeb.tomlConvention(config.ports.hdfsWeb)
        extension.ports.hiveMetastore.tomlConvention(config.ports.hiveMetastore)
        extension.ports.kafka.tomlConvention(config.ports.kafka)
        extension.ports.schemaRegistry.tomlConvention(config.ports.schemaRegistry)
        extension.ports.kafkaUi.tomlConvention(config.ports.kafkaUi)
        extension.ports.s3.tomlConvention(config.ports.s3)
        extension.ports.fakeGcs.tomlConvention(config.ports.fakeGcs)
        extension.ports.icebergRestCatalog.tomlConvention(config.ports.icebergRestCatalog)
        extension.ports.icebergRestCatalogTls.tomlConvention(config.ports.icebergRestCatalogTls)

        extension.containerLogs.mode.tomlConvention(config.containerLogs.mode)
        extension.containerLogs.directory.tomlConvention(config.containerLogs.directory)
        extension.containerLogs.append.tomlConvention(config.containerLogs.append)
        extension.containerLogLevels.tomlConvention(
            config.containerLogLevels.mapKeys { (service, _) -> service.name }.takeIf { it.isNotEmpty() },
        )
        extension.containerCustomizations.tomlConvention(
            config.encodedContainerCustomizations().takeIf { it.isNotEmpty() },
        )
    }

    private fun Project.resolveExtensionConfigLocation(location: String): String {
        if (!location.startsWith("classpath:")) return location
        val resource = location.removePrefix("classpath:").trimStart('/')
        val resourceFile = classpathResourceFile(resource)
        return resourceFile?.let { "file:${it.absolutePath}" } ?: location
    }

    private fun Project.classpathResourceFile(resource: String) =
        resourceSourceSets()
            .asSequence()
            .flatMap { sourceSet ->
                (sourceSet.resources.srcDirs + listOfNotNull(sourceSet.output.resourcesDir)).asSequence()
            }
            .map { it.resolve(resource) }
            .firstOrNull { it.isFile }

    private fun Project.resourceSourceSets() =
        (extensions.findByName("sourceSets") as? SourceSetContainer)
            ?.toList()
            .orEmpty()
            .sortedBy { sourceSet ->
                when (sourceSet.name) {
                    "main" -> 0
                    "test" -> 1
                    else -> 2
                }
            }

    private fun <T : Any> Property<T>.tomlConvention(value: T?) {
        if (value != null) convention(value)
    }

    private fun <K : Any, V : Any> MapProperty<K, V>.tomlConvention(value: Map<K, V>?) {
        if (value != null) convention(value)
    }

    private fun <T : Any> ListProperty<T>.tomlConvention(value: List<T>?) {
        if (value != null) convention(value)
    }

    private data class ExtensionRuntimeNeeds(
        val hadoop: Boolean = false,
        val kafkaAvro: Boolean = false,
        val spark: Boolean = false,
    )
}
