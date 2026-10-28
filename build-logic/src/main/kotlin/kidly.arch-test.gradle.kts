// ArchUnit 규칙 테스트(com.bbcc.kidly.architecture)를 test에서 빼고 archTest로 따로 돌린다. CI에서 단계별로 실패 원인을 구분한다
// 규칙 테스트가 없으면 No tests found로 실패한다. 도메인 모듈은 그 전에 kidly.domain의 checkLayerArchitectureTest가 잡는다
plugins {
    id("kidly.java-conventions")
}

val architecturePackage = "com.bbcc.kidly.architecture.*"

dependencies {
    testImplementation(project(":architecture"))
}

val archTest = tasks.register<Test>("archTest") {
    description = "ArchUnit으로 경계 · 계층 규칙을 검사한다"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching(architecturePackage)
}

tasks.test {
    filter.excludeTestsMatching(architecturePackage)
}

tasks.check {
    dependsOn(archTest)
}
