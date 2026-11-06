plugins {
    id("kidly.domain")
}

dependencies {
    // OpenAI 호출(chat · 임베딩). 버전은 Spring AI BOM이 맞춘다
    implementation(platform(libs.spring.ai.bom))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
}
