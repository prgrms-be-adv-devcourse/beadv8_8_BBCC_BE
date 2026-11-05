dependencies {
    implementation(project(":support"))
    implementation("com.bbcc:shared")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // JWT 발급, PEM 키 읽기, Refresh Token 생성.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Fixture가 사용하는 엔티티의 기반 타입.
    testFixturesImplementation(project(":support"))
    testFixturesImplementation("org.springframework.security:spring-security-crypto")
    testFixturesImplementation("org.springframework:spring-test")

    // archTest가 돌리는 계층 규칙(LayerRules)
    testImplementation(testFixtures(project(":support")))
}
