// 공통 코드(com.bbcc.kidly.global). 자기가 쓰는 것만 넣고, 공개 시그니처에 나오는 것만 api로 전달한다
plugins {
    id("kidly.library")
    // 테스트 도우미(src/testFixtures/java, TestMembers)를 앱 · 도메인 테스트가 같이 쓴다
    `java-test-fixtures`
}

dependencies {
    // EventPublisher가 shared의 DomainEvent를 받는다
    api(project(":shared"))

    // 공개 시그니처에 나온다: ErrorCode(HttpStatus), PageResponse.from(Page), BaseTimeEntity(JPA)
    // api라 common을 쓰는 모듈(도메인 · 앱)의 컴파일 클래스패스에도 들어간다
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // SwaggerConfig(OpenAPI 정의)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // QueryDSL. BaseTimeEntity · BaseSoftDeleteEntity의 Q 클래스를 여기서 만들어야 도메인 Q 클래스가 상속할 수 있다
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}

// JPA 통합 테스트(ReadOnlyWriteGuard · BaseTimeEntity, 실제 PostgreSQL). 서비스마다 같은 설정을 쓰므로 공통 코드와 같이 여기서 확인한다
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}

// 앱 모듈(kidly.boot-app)과 같은 나눔: 단위는 test, 스프링 + Testcontainers는 integrationTest(Docker 필요)
val integrationTag = "integration"

tasks.test {
    useJUnitPlatform {
        excludeTags(integrationTag)
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "common 통합 테스트를 실행한다 (스프링 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}
