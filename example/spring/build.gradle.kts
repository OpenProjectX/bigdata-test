import java.io.File

plugins {
    id("buildsrc.convention.spring-kotlin")
}

description = "Spring Boot example for bigdata-test"

val localBigDataRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.hadoopClientApi)
    runtimeOnly(libs.hadoopClientRuntime)
    runtimeOnly(libs.hadoopAws)
    implementation(libs.awsSdkS3)

    localBigDataRuntime(project(":bigdata-test-spring-boot-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

val localS3JceksFile = layout.buildDirectory.file("spring-local/s3a.jceks").get().asFile
val localS3JceksPath = localS3JceksFile.absolutePath
val localS3JceksParent = localS3JceksFile.parentFile.absolutePath
File(localS3JceksParent).mkdirs()

val resetSpringLocalS3Jceks by tasks.registering(Delete::class) {
    delete(localS3JceksFile)
}

val prepareSpringLocalS3AccessKeyJceks by tasks.registering(JavaExec::class) {
    dependsOn(resetSpringLocalS3Jceks)
    outputs.file(localS3JceksFile)
    outputs.upToDateWhen { false }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.apache.hadoop.security.alias.CredentialShell")
    args(
        "create",
        "fs.s3a.access.key",
        "-value",
        "test",
        "-provider",
        "jceks://file/$localS3JceksPath",
    )
}

val prepareSpringLocalS3SecretKeyJceks by tasks.registering(JavaExec::class) {
    dependsOn(prepareSpringLocalS3AccessKeyJceks)
    outputs.file(localS3JceksFile)
    outputs.upToDateWhen { false }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.apache.hadoop.security.alias.CredentialShell")
    args(
        "create",
        "fs.s3a.secret.key",
        "-value",
        "test",
        "-provider",
        "jceks://file/$localS3JceksPath",
    )
}

tasks.register("prepareSpringLocalS3Jceks") {
    dependsOn(prepareSpringLocalS3SecretKeyJceks)
}

tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunLocal") {
    group = "application"
    description = "Run the Spring example with local bigdata-test containers and S3A JCEKS credentials."
    classpath = sourceSets.main.get().runtimeClasspath + localBigDataRuntime
    mainClass.set("org.openprojectx.bigdata.test.example.spring.BigDataTestExampleApplicationKt")
    dependsOn("prepareSpringLocalS3Jceks")
    systemProperty("spring.profiles.active", "local")
}
