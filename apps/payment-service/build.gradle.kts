// 결제 서비스(#525). 결제 도메인만 띄운다(build-logic/README.md "서비스 모듈 만드는 법")
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:payment"))
    testImplementation(testFixtures(project(":domains:payment")))

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션이다. kidly-app이 적용하던 결제 파일은 이 서비스의 db/migration에 있고, 그 뒤 파일은 도메인 모듈의 db/migration/payment/에 둔다.
    // kidly-app에서 결제를 빼기 전에는 결제 마이그레이션을 더하면 baseline-version을 올린다(application.yml의 baseline 주석)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    // 기능 테스트의 로그인 헤더(TestMembers)
    testImplementation(testFixtures(project(":common")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // 수신 테스트용 Kafka 컨테이너와 메시지를 넣는 Producer (버전은 Boot BOM, kidly-app과 같다)
    // 결제 리스너를 켜고 끄는 레지스트리(spring-kafka)는 도메인 모듈이 implementation으로만 받아 테스트 컴파일 경로에 따로 넣는다
    testImplementation("org.springframework.boot:spring-boot-starter-kafka")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}
