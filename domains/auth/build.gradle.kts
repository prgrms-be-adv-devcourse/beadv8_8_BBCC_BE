plugins {
    id("kidly.domain")
}

dependencies {
    // Access Token 발급(NimbusJwtEncoder)과 PEM 읽기(RsaKeyConverters) · Refresh Token 생성(Base64StringKeyGenerator).
    // nimbus-jose-jwt는 Boot BOM이 버전을 관리하지 않아 Spring Security BOM이 관리하는 jose로 받는다(spring-security-core · crypto가 딸려 온다).
    // 보안 필터 체인(starter-security)은 쓰지 않는다
    implementation("org.springframework.security:spring-security-oauth2-jose")
}

// 뼈대만 있는 동안은 단위 테스트가 없다(규칙 테스트는 archTest). 첫 단위 테스트가 생기면 지운다
tasks.test {
    filter.isFailOnNoMatchingTests = false
}
