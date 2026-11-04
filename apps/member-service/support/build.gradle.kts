dependencies {
    api("com.bbcc:shared")

    // 공개 시그니처: HttpStatus, Page 및 JPA 기반 타입.
    api("org.springframework:spring-web")
    api("org.springframework.data:spring-data-commons")
    api("jakarta.persistence:jakarta.persistence-api")

    // 지원 코드 내부의 auditing 구현.
    implementation("org.springframework.data:spring-data-jpa")

    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // 도메인 모듈이 archTest로 돌리는 계층 규칙(com.bbcc.kidly.architecture)
    testFixturesApi(libs.archunit.junit5)
}
