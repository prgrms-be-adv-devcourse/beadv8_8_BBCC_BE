// 모든 모듈이 공통으로 쓰는 설정: Java 25 · Checkstyle · Lombok · Boot BOM · 테스트 태스크 분리
plugins {
    java
    checkstyle
    id("io.spring.dependency-management")
}

group = "com.bbcc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Boot 플러그인이 없는 라이브러리 모듈도 앱과 같은 버전을 쓰도록 Boot BOM을 가져온다
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // 단위 테스트 (JUnit · AssertJ · Mockito · spring-test). 통합 · 기능 테스트용 의존은 앱 모듈에 둔다
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 설정 파일은 모듈마다 두지 않고 루트의 것 하나를 쓴다
checkstyle {
    toolVersion = "14.3.0"
    maxWarnings = 0
    configDirectory = layout.settingsDirectory.dir("config/checkstyle")
}

// 파라미터 이름을 class 파일에 남긴다. Boot 플러그인이 있는 앱 모듈은 플러그인이 넣어 주지만,
// 도메인 모듈은 없어서 빠지면 @PathVariable · @RequestParam · @Query의 이름 생략 바인딩이 실패한다
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ArchUnit 경계 규칙은 앱 모듈의 archTest에서만 돈다 (kidly.boot-app)
val architecturePackage = "com.bbcc.kidly.architecture.*"

// 테스트 종류는 JUnit 태그로 나눈다. test는 Docker 없이 도는 단위 테스트만 실행한다
val integrationTag = "integration"
val functionalTag = "functional"

tasks.test {
    description = "단위 테스트를 실행한다 (스프링·DB 없음)"
    filter.excludeTestsMatching(architecturePackage)
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
