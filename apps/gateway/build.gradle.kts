// API Gateway(#608). nginx 뒤에서 JWT 검증 · 즉시 무효화 확인 · X-Member-* 헤더 · CORS · 라우팅만 한다(업무 로직 없음)
// WebFlux(Spring Cloud Gateway)라 서블릿 보안 설정이 든 common에는 의존하지 않는다. 이벤트 계약(shared)만 쓴다
plugins {
    id("kidly.boot-app")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // 공개키로 JWT를 검증한다(NimbusReactiveJwtDecoder). 보안 필터 체인(starter-security)은 쓰지 않는다
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // member.tokens-revoked 수신, 읽지 못한 메시지는 DLT로
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // 헬스체크 · 메트릭
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
}
