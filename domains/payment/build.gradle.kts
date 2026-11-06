plugins {
    id("kidly.domain")
}

dependencies {
    // 결제 업무 · 아웃박스 · DLT 지표(#786). 버전은 Boot BOM
    implementation("io.micrometer:micrometer-core")
}
