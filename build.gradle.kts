import com.google.protobuf.gradle.id

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
}

val group: String by project
val version: String by project

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.bom.get().toString())
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

dependencies {
    // Spring Boot Starters
    implementation(libs.bundles.spring.boot)
    developmentOnly(libs.spring.boot.devtools)
    annotationProcessor(libs.spring.boot.configuration.processor)

    // gRPC
    implementation(libs.bundles.grpc)
    implementation(libs.bundles.grpc.spring)

    // Protocol Buffers
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)

    // Kubernetes
    implementation(libs.bundles.kubernetes)

    // Monitoring
    implementation(libs.bundles.monitoring)

    // Utilities
    implementation(libs.bundles.utilities)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.wiremock.standalone)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.33.4"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.78.0"
        }
        id("reactor-grpc") {
            artifact = "com.salesforce.servicelibs:reactor-grpc:${libs.versions.reactor.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("reactor-grpc")
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc",
                "build/generated/source/proto/main/reactor-grpc"
            )
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jar{
    enabled=false
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
