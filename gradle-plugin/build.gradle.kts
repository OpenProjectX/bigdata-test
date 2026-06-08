plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

description = "Gradle plugin that manages bigdata-test containers outside the application runtime"

dependencies {
    api(project(":core"))
    runtimeOnly(libs.slf4jSimple)
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

gradlePlugin {
    plugins {
        create("bigDataTest") {
            id = "org.openprojectx.bigdata-test"
            implementationClass = "org.openprojectx.bigdata.test.gradle.BigDataTestGradlePlugin"
            displayName = "bigdata-test Gradle plugin"
            description = "Starts bigdata-test containers in Gradle and injects endpoint properties into app and test tasks"
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set(project.description)
            url.set("https://github.com/OpenProjectX/bigdata-test")

            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }

            developers {
                developer {
                    id.set("OpenProjectX")
                    name.set("OpenProjectX")
                    email.set("admin@openprojectx.org")
                }
            }

            scm {
                url.set("https://github.com/OpenProjectX/bigdata-test")
                connection.set("scm:git:https://github.com/OpenProjectX/bigdata-test.git")
                developerConnection.set("scm:git:ssh://git@github.com:OpenProjectX/bigdata-test.git")
            }
        }
    }
}
