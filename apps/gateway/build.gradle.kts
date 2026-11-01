// API Gateway(#608). nginx 뒤에서 JWT 검증 · 즉시 무효화 확인 · X-Member-* 헤더 · CORS · 라우팅만 한다(업무 로직 없음)
// WebFlux(Spring Cloud Gateway)라 서블릿(webmvc) · JPA 기반인 common에는 의존하지 않는다. 이벤트 · 헤더 계약(shared)만 쓴다
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
    // 정규화되지 않거나 위험한 요청(.. · // · %2f · ; 등)을 라우팅 · 역할 검사보다 먼저 400으로 막는다(StrictServerWebExchangeFirewall).
    // 방화벽 클래스만 쓴다. 이것만으로는 보안 자동 설정(spring-boot-security)이 들어오지 않는다
    implementation("org.springframework.security:spring-security-web")
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
