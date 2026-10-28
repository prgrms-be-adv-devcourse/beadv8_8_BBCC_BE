// 도메인 모듈. 도메인마다 같은 스택을 넣어 담당자가 빌드 파일을 고치지 않고 코드만 옮길 수 있게 한다
plugins {
    id("kidly.library")
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
}
