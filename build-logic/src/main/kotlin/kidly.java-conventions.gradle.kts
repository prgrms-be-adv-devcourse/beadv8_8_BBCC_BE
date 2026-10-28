// 모든 모듈이 공통으로 쓰는 설정: Java 25 · Checkstyle · Lombok · Boot BOM · JUnit
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

// 테스트 종류별 태스크(integrationTest · functionalTest)는 앱 모듈에만 있다 (kidly.boot-app).
// 여기서 태그를 빼지 않는다: 태그 붙은 테스트가 라이브러리 · 도메인 모듈에 잘못 들어오면 돌릴 태스크가 없어
// 조용히 빠지기보다 test에서 드러나는 편이 낫다
tasks.withType<Test> {
    useJUnitPlatform()
}
