plugins {
    id("kidly.boot-app")
}

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

    // 스키마 마이그레이션 (src/main/resources/db/migration)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // 일괄 처리 (정산 실행). 실행 기록을 DB에 남겨 같은 작업의 재실행을 막는다
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")

    // QueryDSL (OpenFeign fork)
    implementation("io.github.openfeign.querydsl:querydsl-jpa:${Versions.QUERYDSL}")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:${Versions.QUERYDSL}:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // .env 읽기 (따옴표 · export 등 .env 문법 지원). 테스트 프로필에서는 끈다
    implementation("me.paulschwarz:springboot4-dotenv:${Versions.SPRING_DOTENV}")

    // API 문서
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.SPRINGDOC}")

    // 상품 이미지를 브라우저가 S3에 직접 올리도록 업로드 주소(Presigned URL)를 만든다 (S3Presigner)
    implementation("software.amazon.awssdk:s3:${Versions.AWS_SDK}")

    // 헬스체크 (/actuator/health). 배포 시 docker compose healthcheck가 호출한다
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 메트릭 (/actuator/prometheus). 서버의 Prometheus 가 127.0.0.1 로 수집한다. 외부는 nginx 가 막는다
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // 메일 발송 (global.mail.EmailSender, Gmail SMTP). kidly.mail.enabled가 false면 보내지 않고 로그만 남긴다
    implementation("org.springframework.boot:spring-boot-starter-mail")

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
    testImplementation("com.tngtech.archunit:archunit-junit5:${Versions.ARCHUNIT}")
}
