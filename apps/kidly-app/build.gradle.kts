plugins {
    id("kidly.boot-app")
    // 모듈 사이 경계 규칙(ArchitectureTest)을 검사한다. 모듈 안의 계층 규칙은 도메인 모듈마다 검사한다
    id("kidly.arch-test")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))

    // 도메인 모듈과 그 Fixture(src/testFixtures). 통합 · 기능 테스트가 도메인 Fixture를 쓴다
    listOf(
        "member", "seller", "childprofile", "setting", "notification",
        "product", "recommend", "order", "payment", "settlement",
    ).forEach { domain ->
        implementation(project(":domains:$domain"))
        testImplementation(testFixtures(project(":domains:$domain")))
    }

    // --- 앱(서비스)에 남는 것 ---

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션. 기존 파일은 이 앱의 db/migration, 새 파일은 도메인 모듈의 db/migration/{스키마}/ (Flyway가 모든 jar의 하위 폴더까지 읽는다)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기 (따옴표 · export 등 .env 문법 지원). 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI). 실행되는 앱(서비스)이 화면을 연다
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health). 배포 시 docker compose healthcheck가 호출한다
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 메트릭 (/actuator/prometheus). 서버의 Prometheus 가 127.0.0.1 로 수집한다. 외부는 nginx 가 막는다
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

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
    // 테스트용 Kafka 컨테이너와 그 확인 테스트의 Producer · Admin (버전은 Boot BOM)
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}
