dependencies {
    implementation(project(":support"))
    implementation("com.bbcc:shared")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // 저장소의 QueryDSL 쿼리와 엔티티 Q 클래스 생성.
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // Fixture가 사용하는 엔티티의 기반 타입.
    testFixturesImplementation(project(":support"))

    // archTest가 돌리는 계층 규칙(LayerRules)
    testImplementation(testFixtures(project(":support")))
}
