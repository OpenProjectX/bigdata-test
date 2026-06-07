plugins {
    id("buildsrc.convention.spring-kotlin")
}

description = "Spring Boot example for bigdata-test"

val isCi = gradle.extra["isCi"] as Boolean

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.hadoopClientApi)
    runtimeOnly(libs.hadoopClientRuntime)
    runtimeOnly(libs.hadoopAws)
    implementation(libs.awsSdkS3)

//    if (!isCi) {
//        runtimeOnly(project(":bigdata-test-spring-boot-starter"))
//        runtimeOnly(project(":extensions"))
//    }
    implementation("org.apache.commons:commons-lang3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunLocal") {
    group = "application"
    description = "Run the Spring example with local bigdata-test containers and extension-provisioned S3A JCEKS credentials."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openprojectx.bigdata.test.example.spring.BigDataTestExampleApplicationKt")
    systemProperty("spring.profiles.active", "local")
}
