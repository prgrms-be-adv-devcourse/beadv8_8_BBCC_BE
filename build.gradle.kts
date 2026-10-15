plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bbcc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val querydslVersion = "7.7"
val springdocVersion = "3.1.1"
val archunitVersion = "1.5.1"
val springDotenvVersion = "5.1.0"
val logstashEncoderVersion = "9.0"

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security (JWT RS256 검증)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // 일괄 처리 (정산 실행). 실행 기록을 DB에 남겨 같은 작업의 재실행을 막는다
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")

    // QueryDSL (OpenFeign fork)
    implementation("io.github.openfeign.querydsl:querydsl-jpa:$querydslVersion")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:$querydslVersion:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // .env 읽기 (따옴표 · export 등 .env 문법 지원). 테스트 프로필에서는 끈다
    implementation("me.paulschwarz:springboot4-dotenv:$springDotenvVersion")

    // 로그를 JSON으로 Logstash에 보낸다 (logback-spring.xml의 LOGSTASH)
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // API 문서
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // 헬스체크 (/actuator/health). 배포 시 docker compose healthcheck가 호출한다
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    toolVersion = "14.3.0"
    maxWarnings = 0
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ArchUnit 경계 규칙은 archTest로 분리해 CI에서 단계별로 실패 원인을 구분한다
val architecturePackage = "com.bbcc.kidly.architecture.*"

val archTest = tasks.register<Test>("archTest") {
    description = "ArchUnit으로 모듈 경계 규칙을 검사한다"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching(architecturePackage)
}

// 테스트 종류는 JUnit 태그로 나눈다. test는 Docker 없이 도는 단위 테스트만 실행한다
val integrationTag = "integration"
val functionalTag = "functional"

tasks.test {
    description = "단위 테스트를 실행한다 (스프링·DB 없음)"
    filter.excludeTestsMatching(architecturePackage)
    useJUnitPlatform {
        excludeTags(integrationTag, functionalTag)
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "통합 테스트를 실행한다 (스프링 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    shouldRunAfter(tasks.test)
}

val functionalTest = tasks.register<Test>("functionalTest") {
    description = "기능 테스트를 실행한다 (실제 포트 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(functionalTag)
    }
    shouldRunAfter(integrationTest)
}

tasks.check {
    dependsOn(archTest, integrationTest, functionalTest)
}
