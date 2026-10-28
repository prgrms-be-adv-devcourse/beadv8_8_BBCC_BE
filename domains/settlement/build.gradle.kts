plugins {
    id("kidly.domain")
}

dependencies {
    // 일괄 처리 (정산 실행). 실행 기록을 DB에 남겨 같은 작업의 재실행을 막는다
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
}
