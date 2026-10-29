plugins {
    id("kidly.domain")
}

dependencies {
    // 검색 색인 · 조회는 Elasticsearch에 한다. 버전은 Boot BOM(elasticsearch-java 9.4.x)을 따른다
    implementation("org.springframework.boot:spring-boot-starter-elasticsearch")
}
