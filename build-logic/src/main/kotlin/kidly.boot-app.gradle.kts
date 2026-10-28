// 실행 가능한 앱(서비스) 모듈: Boot 플러그인(bootJar · bootRun) + 테스트 종류별 태스크
// 통합 · 기능 테스트는 전체 컨텍스트 · 마이그레이션이 있는 앱(서비스) 모듈에 산다
plugins {
    id("kidly.java-conventions")
    id("org.springframework.boot")
}

// 테스트 종류는 JUnit 태그로 나눈다. test는 Docker 없이 도는 단위 테스트만 실행한다
val integrationTag = "integration"
val functionalTag = "functional"

tasks.test {
    description = "단위 테스트를 실행한다 (스프링·DB 없음)"
    useJUnitPlatform {
        excludeTags(integrationTag, functionalTag)
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "통합 테스트를 실행한다 (스프링 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTag)
    }
    shouldRunAfter(tasks.test)
}

val functionalTest = tasks.register<Test>("functionalTest") {
    description = "기능 테스트를 실행한다 (실제 포트 + Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags(functionalTag)
    }
    shouldRunAfter(integrationTest)
}

tasks.check {
    dependsOn(integrationTest, functionalTest)
}

// 설정의 상대 경로(file:config/jwt/..., .env)는 루트 기준이라 실행 · 테스트를 루트에서 한다
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

tasks.withType<Test> {
    workingDir = rootDir
}
