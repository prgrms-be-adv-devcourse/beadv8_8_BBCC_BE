// 공통 코드(com.bbcc.kidly.global). 자기가 쓰는 것만 넣고, 공개 시그니처에 나오는 것만 api로 전달한다
plugins {
    id("kidly.library")
    // 테스트 도우미(src/testFixtures/java, TestMembers)를 앱 · 도메인 테스트가 같이 쓴다
    `java-test-fixtures`
}

dependencies {
    // EventPublisher가 shared의 DomainEvent를 받는다
    api(project(":shared"))

    // 공개 시그니처에 나온다: ErrorCode(HttpStatus), PageResponse.from(Page), BaseTimeEntity(JPA), AuthMember.from(Jwt)
    // api라 common을 쓰는 모듈(도메인 · 앱)의 컴파일 클래스패스에도 들어간다. resource-server는 starter-security를 포함한다
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // SwaggerConfig(OpenAPI 정의)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // QueryDSL. BaseTimeEntity · BaseSoftDeleteEntity의 Q 클래스를 여기서 만들어야 도메인 Q 클래스가 상속할 수 있다
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}
