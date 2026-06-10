pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }

    fun rootBuildVersion(): String? {
        val propertiesFile = settingsDir.resolve("../../gradle.properties").normalize()
        if (!propertiesFile.isFile) return null
        return propertiesFile.readLines()
            .map(String::trim)
            .firstOrNull { it.startsWith("version") && it.contains("=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    val bigDataTestPluginVersion =
        providers.gradleProperty("bigdataTestPluginVersion")
            .orElse(providers.environmentVariable("BIGDATA_TEST_PLUGIN_VERSION"))
            .orElse(providers.provider { rootBuildVersion() })

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.openprojectx.bigdata-test" && bigDataTestPluginVersion.isPresent) {
                useVersion(bigDataTestPluginVersion.get())
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://packages.confluent.io/maven/")
    }
}

rootProject.name = "bigdata-test-spring-gradle-plugin-example"
