// 정산 서비스(#729). 정산 도메인만 띄운다(build-logic/README.md "서비스 모듈 만드는 법")
plugins {
    id("kidly.boot-app")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":domains:settlement"))
    testImplementation(testFixtures(project(":domains:settlement")))

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션. init은 이 서비스의 db/migration, 그 뒤 파일은 도메인 모듈의 db/migration/settlement/
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
    // 정산 작업 테스트가 실행 기록(JobRepository · JobParameters)을 직접 만든다. 도메인 모듈은 batch를 implementation으로 받아
    // 앱의 테스트 컴파일 경로에는 없다 (kidly-app과 같은 의존)
    testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Kafka 테스트용 컨테이너와 메시지를 넣고 읽는 Producer · Consumer, 리스너를 켜고 끄는 레지스트리 (버전은 Boot BOM, kidly-app과 같다)
    // 도메인 모듈은 spring-kafka를 implementation으로 받아 앱의 테스트 컴파일 경로에는 없다.
    // 실행 경로에는 도메인 모듈이 넣으므로 main에는 넣지 않는다(kidly-app의 앱 공용 Kafka 설정 같은 코드가 없다)
    testImplementation("org.springframework.boot:spring-boot-starter-kafka")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}
