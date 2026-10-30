// 검색 서비스(#526). 검색 도메인만 띄운다. 상품 목록 API와 경로가 같아 kidly-app과 따로 띄운다
// DB를 쓰지 않아 PostgreSQL 드라이버 · Flyway는 넣지 않는다(build-logic/README.md "서비스 모듈 만드는 법")
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:search"))
    testImplementation(testFixtures(project(":domains:search")))

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health, Elasticsearch 연결 포함) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    // 색인을 비우고 새로 고치는 테스트 지원이 Elasticsearch 클라이언트를 직접 쓴다(도메인 모듈은 implementation이라 보이지 않는다)
    testImplementation("org.springframework.boot:spring-boot-starter-elasticsearch")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
