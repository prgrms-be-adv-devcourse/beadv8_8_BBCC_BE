plugins {
    id("kidly.domain")
}

dependencies {
    // 검색 색인 · 조회는 Elasticsearch에 한다. 버전은 Boot BOM(elasticsearch-java 9.4.x)을 따른다
    implementation("org.springframework.boot:spring-boot-starter-elasticsearch")
    // 장애 감지 지표(임베딩 차단 열림 · 벡터 없는 판매중 상품 수, #1026). 버전은 Boot BOM. 레지스트리는 검색 서비스가 넣는다
    implementation("io.micrometer:micrometer-core")
}
