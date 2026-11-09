plugins {
    id("kidly.domain")
}

// 주문 통합 · 기능 · Kafka 테스트를 이 모듈에서 돌린다(#616). 다른 도메인 모듈에는 의존하지 않는다(kidly.domain이 막는다).
// 테스트 앱은 주문 · common · shared만으로 뜬다(src/test의 OrderTestApplication)
dependencies {
    // 택배 조회 호출의 서킷브레이커(#708). 코어만 쓰고(스타터 · 자동 설정 · 전역 resilience4j.* 없음) delivery/out 안에서 만들어 쓴다.
    // 공용 버전 관리(libs.versions.toml)에 넣지 않고 주문 모듈에서만 버전을 적는다. 전이 의존은 resilience4j-core · slf4j-api(Boot BOM 버전)뿐
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    // 주문 운영 조회 MCP 창구(#925, 읽기 전용). mcp-core(서블릿 전송 포함) + Jackson 3 매퍼. Spring 의존 없이 in/config에서 서블릿으로 등록한다.
    // 공용 버전 관리에 넣지 않고 주문 모듈에서만 버전을 적는다(#925)
    // 전이 의존: reactor-core · Jackson 3(Boot BOM 관리), 새로 들어옴 com.networknt:json-schema-validator 3.0.6 · com.ethlo.time:itu,
    // servlet-api는 provided(Boot Tomcat 것을 씀)
    implementation("io.modelcontextprotocol.sdk:mcp:2.1.0")
    // 주문 운영 지표(#1067). 버전은 Boot BOM이 정한다(결제 #981과 같음). 레지스트리 · actuator는 common이 넣는다
    implementation("io.micrometer:micrometer-core")
    // 기능 테스트의 로그인 헤더(common TestMembers)
    testImplementation(testFixtures(project(":common")))
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
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
