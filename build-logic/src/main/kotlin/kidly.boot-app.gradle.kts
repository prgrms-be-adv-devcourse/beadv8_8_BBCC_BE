// 실행 가능한 앱 모듈: Boot 플러그인(bootJar · bootRun) + 경계 규칙 검사(archTest)
plugins {
    id("kidly.java-conventions")
    id("org.springframework.boot")
}

// ArchUnit 경계 규칙은 archTest로 분리해 CI에서 단계별로 실패 원인을 구분한다
val architecturePackage = "com.bbcc.kidly.architecture.*"

val archTest = tasks.register<Test>("archTest") {
    description = "ArchUnit으로 모듈 경계 규칙을 검사한다"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching(architecturePackage)
}

tasks.check {
    dependsOn(archTest)
}

// 설정의 상대 경로(file:config/jwt/..., .env)는 루트 기준이라 실행 · 테스트를 루트에서 한다
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

tasks.withType<Test> {
    workingDir = rootDir
}
