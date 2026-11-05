// 알림 서비스. 알림 도메인만 띄운다(build-logic/README.md "서비스 모듈 만드는 법")
// HTTP API가 없어(회원 이벤트를 Kafka로 받아 메일만 보낸다) Swagger UI는 넣지 않는다
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:notification"))
    testImplementation(testFixtures(project(":domains:notification")))

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션. init은 이 서비스의 db/migration, 그 뒤 파일은 도메인 모듈의 db/migration/notification/
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // 헬스체크 (/actuator/health) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Kafka 테스트용 컨테이너와 메시지를 넣고 읽는 Producer · Consumer, 리스너를 켜고 끄는 레지스트리 (버전은 Boot BOM)
    // 도메인 모듈은 spring-kafka를 implementation으로 받아 앱의 테스트 컴파일 경로에는 없다.
    // 실행 경로에는 도메인 모듈이 넣으므로 main에는 넣지 않는다(정산 서비스와 같다)
    testImplementation("org.springframework.boot:spring-boot-starter-kafka")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}
