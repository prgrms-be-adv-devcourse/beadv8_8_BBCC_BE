// 챗봇 도메인(#1191). 회원 질문에 답할 문서(RAG)를 가진다. 다른 도메인 모듈에는 의존하지 않는다(kidly.domain이 막는다)
plugins {
    id("kidly.domain")
}

dependencies {
    // OpenAI 호출(chat · 임베딩). 버전은 Spring AI BOM이 맞춘다
    implementation(platform(libs.spring.ai.bom))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
}
