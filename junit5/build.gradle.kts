plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":core"))
    api(libs.junitJupiterApi)
    api(libs.testcontainersJunitJupiter)
}
