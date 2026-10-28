// 도메인 모듈. 도메인마다 같은 스택을 넣어 담당자가 빌드 파일을 고치지 않고 코드만 옮길 수 있게 한다
plugins {
    id("kidly.library")
    // Fixture · 테스트 헬퍼(src/testFixtures/java)를 이 모듈 단위 테스트와 앱(서비스) 모듈 테스트가 같이 쓴다
    `java-test-fixtures`
}

// precompiled script에서는 libs 접근자가 생기지 않아 catalog를 이름으로 찾는다
val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security (member가 JWT를 발급한다)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // QueryDSL (OpenFeign fork)
    implementation(libs.findLibrary("querydsl-jpa").get())
    annotationProcessor(variantOf(libs.findLibrary("querydsl-apt").get()) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // API 문서
    implementation(libs.findLibrary("springdoc-openapi-webmvc-ui").get())

    // Fixture: ReflectionTestUtils(spring-test)로 id를 넣고, 검증 헬퍼는 AssertJ를 쓴다
    testFixturesCompileOnly("org.projectlombok:lombok")
    testFixturesAnnotationProcessor("org.projectlombok:lombok")
    testFixturesImplementation("org.springframework:spring-test")
    testFixturesImplementation("org.assertj:assertj-core")
}

// Fixture는 도메인 엔티티(BaseTimeEntity 상속 등)를 만들므로 main과 같은 의존(common · JPA 등)으로 컴파일한다
configurations.testFixturesImplementation {
    extendsFrom(configurations.implementation.get())
}
