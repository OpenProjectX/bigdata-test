plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "Application-specific JUnit extension consuming bigdata-test context"

dependencies {
    api(project(":example:app-extension:dummy-app-framework"))
    api(project(":junit5"))
    api(project(":extensions"))
    api(libs.junitJupiterApi)
}
