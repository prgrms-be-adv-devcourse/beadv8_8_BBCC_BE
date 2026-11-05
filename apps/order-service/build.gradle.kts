// 주문 서비스. 주문 도메인(주문 · 장바구니 · 배송 · 조르기)만 띄운다(build-logic/README.md "서비스 모듈 만드는 법")
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:order"))
    testImplementation(testFixtures(project(":domains:order")))

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션이다. init과 kidly-app에서 가져온 옛 주문 파일은 이 서비스의 db/migration에, 그 뒤 파일은 도메인 모듈의 db/migration/orders/에 있다
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Kafka 자동 설정. 실행 경로에는 도메인 모듈도 넣지만(implementation), 서비스가 뜨는 데 필요한 런타임이라 다른 서비스와 같이 둔다
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Test
    // 기능 테스트의 로그인 헤더(TestMembers)
    testImplementation(testFixtures(project(":common")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
