plugins {
    id("kidly.domain")
}

// 주문 통합 · 기능 · Kafka 테스트를 이 모듈에서 돌린다(#616). 다른 도메인 모듈에는 의존하지 않는다(kidly.domain이 막는다).
// 테스트 앱은 주문 · common · shared만으로 뜬다(src/test의 OrderTestApplication)
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}

// 앱 모듈(kidly.boot-app)과 같은 나눔: 단위는 test, 스프링 + Testcontainers는 integrationTest · functionalTest(Docker 필요)
val integrationTag = "integration"
val functionalTag = "functional"

tasks.test {
    useJUnitPlatform {
        excludeTags(integrationTag, functionalTag)
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "주문 통합 테스트를 실행한다 (스프링 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    shouldRunAfter(tasks.test)
}

val functionalTest = tasks.register<Test>("functionalTest") {
    description = "주문 기능 테스트를 실행한다 (실제 포트 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(functionalTag)
    }
    shouldRunAfter(integrationTest)
}

tasks.check {
    dependsOn(integrationTest, functionalTest)
}

// 테스트 설정의 JWT 키(config/jwt/local-*.pem)가 루트 기준 상대 경로라 루트에서 실행한다
tasks.withType<Test> {
    workingDir = rootDir
}
