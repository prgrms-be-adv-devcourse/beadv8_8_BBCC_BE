plugins {
    id("kidly.domain")
}

dependencies {
    // 결제 이벤트를 커밋 뒤 Kafka로도 보낸다 (out.kafka). spring-kafka만 넣으면 Boot 4에서 Kafka 자동 설정이 잡히지 않는다
    implementation("org.springframework.boot:spring-boot-starter-kafka")
}
