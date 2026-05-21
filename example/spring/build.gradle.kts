plugins {
    id("buildsrc.convention.spring-kotlin")
}

description = "Spring Boot example for bigdata-test"

dependencies {
    implementation(project(":bigdata-test-spring-boot-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
