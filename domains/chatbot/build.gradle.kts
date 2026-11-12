// 챗봇 도메인(#1191). 회원 질문에 답할 문서(RAG)를 가진다. 다른 도메인 모듈에는 의존하지 않는다(kidly.domain이 막는다)
plugins {
    id("kidly.domain")
}

// 아직 단위 테스트가 없다(계층 규칙은 archTest가 돈다). 첫 단위 테스트를 더하면 이 설정을 지운다
tasks.test {
    filter.isFailOnNoMatchingTests = false
}
