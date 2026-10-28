// 공통 코드(com.bbcc.kidly.global). 자기가 쓰는 것만 넣고, 공개 시그니처에 나오는 것만 api로 전달한다
plugins {
    id("kidly.library")
}

dependencies {
    // EventPublisher가 shared의 DomainEvent를 받는다
    api(project(":shared"))

    // 공개 시그니처에 나온다: ErrorCode(HttpStatus), PageResponse(Page), BaseTimeEntity(JPA), AuthMember(Jwt)
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.springdoc.openapi.webmvc.ui)

    // QueryDSL. BaseTimeEntity · BaseSoftDeleteEntity의 Q 클래스를 여기서 만들어야 도메인 Q 클래스가 상속할 수 있다
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}
