plugins {
    id("kidly.domain")
}

dependencies {
    // Access Token 발급(NimbusJwtEncoder)과 Refresh Token 생성(Base64StringKeyGenerator)
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
}
