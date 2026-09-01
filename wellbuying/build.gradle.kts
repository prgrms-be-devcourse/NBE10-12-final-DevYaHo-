plugins {
    java
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "wellbuying"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["snippetsDir"] = file("build/generated-snippets")

val querydslVersion = "7.5"  // https://openfeign.github.io/querydsl/ 에서 최신 버전 확인 후 필요시 교체

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // RestClient.Builder 자동 구성 - Boot 4에서 webmvc 스타터에 포함되지 않아 별도로 추가한다 (TossPaymentGateway가 주입받음)
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restdocs")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.github.openfeign.querydsl:querydsl-jpa:$querydslVersion")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:$querydslVersion:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
}

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}

val querydslDir = "build/generated/querydsl"

sourceSets {
    main {
        java {
            srcDirs += file(querydslDir)
        }
    }
}

tasks.withType<JavaCompile> {
    options.generatedSourceOutputDirectory.set(file(querydslDir))
}

tasks.named("clean") {
    doLast {
        file(querydslDir).deleteRecursively()
    }
}