plugins {
    id("kidly.domain")
}

dependencies {
    // OpenAI 호출(chat · 임베딩). 버전은 Spring AI BOM이 맞춘다
    implementation(platform(libs.spring.ai.bom))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // 일괄 처리(선호 점수 매일 계산). 실행 기록을 DB에 남겨 같은 기준일을 두 번 완료하지 않고 실패한 곳부터 다시 돈다
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")

    // AI 답을 24시간 저장해 다시 쓴다(Redis). 하루 호출 횟수는 DB에 둔다
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
