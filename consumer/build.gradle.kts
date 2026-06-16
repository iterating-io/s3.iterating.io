plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "io.iterating.s3.consumer"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(project(":nats"))
    implementation("software.amazon.awssdk:s3")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:2.32.12")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.3")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}