// 회원 서비스 실행 앱. 회원 · 인증 · 판매자 도메인 모듈과 서비스 공통 support를 묶어 띄운다
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management) apply false
}

val domains = listOf("auth", "member", "seller", "childprofile")

// 앱과 모듈이 함께 쓰는 설정: Java 25 · Checkstyle · Lombok · Boot BOM · JUnit
allprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "io.spring.dependency-management")

    group = "com.bbcc"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    // Boot 플러그인이 없는 모듈도 앱과 같은 버전을 쓰도록 Boot BOM을 가져온다
    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")

        // 단위 테스트 (JUnit · AssertJ · Mockito · spring-test). 통합 · 기능 테스트용 의존은 앱에 둔다
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "14.3.0"
        maxWarnings = 0
        // 코드 스타일은 팀 합의라 저장소 공통 설정(루트 config/checkstyle)을 쓴다
        configDirectory = rootProject.layout.projectDirectory.dir("../../config/checkstyle")
    }

    // 파라미터 이름을 class 파일에 남긴다. Boot 플러그인이 없는 모듈에서 빠지면 @PathVariable 등의 이름 생략 바인딩이 실패한다
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "java-test-fixtures")

    // 도메인 사이 참조는 의존으로 막는다. support도 도메인을 참조하지 않는다
    configurations.configureEach {
        dependencies.withType<ProjectDependency>().configureEach {
            if (name in domains && name != project.name) {
                throw GradleException("${project.path}가 도메인 모듈 $path 에 의존한다. 도메인 사이는 shared 계약으로만 주고받는다")
            }
        }
    }
}

// ArchUnit 계층 규칙(com.bbcc.kidly.architecture)은 test에서 빼고 archTest로 따로 돌린다
configure(subprojects.filter { it.name in domains }) {
    val architecturePackage = "com.bbcc.kidly.architecture.*"

    tasks.named<Test>("test") {
        filter.excludeTestsMatching(architecturePackage)
    }

    val archTest = tasks.register<Test>("archTest") {
        description = "ArchUnit으로 계층 규칙을 검사한다"
        group = "verification"
        val test = project.the<SourceSetContainer>()["test"]
        testClassesDirs = test.output.classesDirs
        classpath = test.runtimeClasspath
        // 규칙 테스트(LayerArchitectureTest)가 빠지면 No tests found로 실패한다
        filter.includeTestsMatching(architecturePackage)
    }

    tasks.named("check") {
        dependsOn(archTest)
    }
}

dependencies {
    implementation(project(":support"))
    implementation("com.bbcc:shared")
    domains.forEach { domain ->
        implementation(project(":$domain"))
        testImplementation(testFixtures(project(":$domain")))
    }

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(libs.querydsl.jpa)

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 스키마 마이그레이션. kidly-app이 적용하던 회원 · 판매자 파일은 이 서비스의 db/migration, 그 뒤 파일은 도메인 모듈의 db/migration/{auth,member}/
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // .env 읽기. 테스트 프로필에서는 끈다
    implementation(libs.spring.dotenv)

    // API 문서 (Swagger UI)
    implementation(libs.springdoc.openapi.webmvc.ui)

    // 헬스체크 (/actuator/health) · 메트릭 (/actuator/prometheus)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    // 기능 테스트의 로그인 헤더(TestMembers · TestAdmins)
    testImplementation(testFixtures(project(":support")))
    // 발급한 Access Token을 읽는다(support.IssuedAccessToken, nimbus-jose-jwt). 버전은 Spring Security BOM이 관리한다
    testImplementation("org.springframework.security:spring-security-oauth2-jose")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Kafka 테스트용 컨테이너와 메시지를 넣고 읽는 Producer · Consumer, 리스너를 켜고 끄는 레지스트리
    // 도메인 모듈은 spring-kafka를 implementation으로 받아 앱의 테스트 컴파일 경로에는 없다
    testImplementation("org.springframework.boot:spring-boot-starter-kafka")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.apache.kafka:kafka-clients")
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
