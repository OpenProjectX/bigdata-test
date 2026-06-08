pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }

    val bigDataTestPluginVersion =
        providers.gradleProperty("bigdataTestPluginVersion")
            .orElse(providers.environmentVariable("BIGDATA_TEST_PLUGIN_VERSION"))

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
