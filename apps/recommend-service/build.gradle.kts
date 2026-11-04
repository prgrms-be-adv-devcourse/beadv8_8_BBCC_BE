// 추천 서비스(#704). 추천 도메인만 띄운다(build-logic/README.md "서비스 모듈 만드는 법")
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:recommend"))
    testImplementation(testFixtures(project(":domains:recommend")))

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션이다. init은 이 서비스의 db/migration에 있고, 그 뒤 파일은 도메인 모듈의 db/migration/recommend/에 둔다
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Kafka 전송 실패 로그 설정이 ProducerListener를 쓴다. 도메인 모듈은 implementation으로 받아 서비스 컴파일 경로에 없다
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Test
    // 기능 테스트의 로그인 헤더(TestMembers)
    testImplementation(testFixtures(project(":common")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // 수신 테스트용 Kafka 컨테이너와 메시지를 넣는 Producer · Admin (버전은 Boot BOM, kidly-app과 같다)
    // 리스너를 켜고 끄는 레지스트리는 위의 main 의존(spring-boot-starter-kafka)이 테스트에도 준다
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}
