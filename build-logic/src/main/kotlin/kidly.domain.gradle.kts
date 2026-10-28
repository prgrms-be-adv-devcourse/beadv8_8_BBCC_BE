// 도메인 모듈. 도메인마다 같은 스택을 넣어 담당자가 빌드 파일을 고치지 않고 코드만 옮길 수 있게 한다
plugins {
    id("kidly.library")
    // Fixture · 테스트 헬퍼(src/testFixtures/java)를 이 모듈 단위 테스트와 앱(서비스) 모듈 테스트가 같이 쓴다
    `java-test-fixtures`
    // 모듈 안의 계층 규칙(in → app → domain, out → app.port · domain)은 도메인마다 archTest로 검사한다
    id("kidly.arch-test")
}

// precompiled script에서는 libs 접근자가 생기지 않아 catalog를 이름으로 찾는다
val libs = the<VersionCatalogsExtension>().named("libs")

// 도메인 사이 참조는 의존으로 막는다. 다른 도메인 모듈에 의존하지 않으면 그 코드를 쓸 때 컴파일이 안 된다
val modulePath = path
configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        if (path.startsWith(":domains:") && path != modulePath) {
            throw GradleException(
                "$modulePath 가 다른 도메인 모듈 $path 에 의존한다. 도메인 사이는 shared의 이벤트로만 주고받는다")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // QueryDSL (OpenFeign fork)
    implementation(libs.findLibrary("querydsl-jpa").get())
    annotationProcessor(variantOf(libs.findLibrary("querydsl-apt").get()) { classifier("jpa") })
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // Fixture: ReflectionTestUtils(spring-test)로 id를 넣고, 검증 헬퍼는 AssertJ를 쓴다
    testFixturesCompileOnly("org.projectlombok:lombok")
    testFixturesAnnotationProcessor("org.projectlombok:lombok")
    testFixturesImplementation("org.springframework:spring-test")
    testFixturesImplementation("org.assertj:assertj-core")
}

// Fixture는 도메인 엔티티(BaseTimeEntity 상속 등)를 만들므로 main과 같은 의존(common · JPA 등)으로 컴파일한다
configurations.testFixturesImplementation {
    extendsFrom(configurations.implementation.get())
}

// 테스트가 하나도 없으면 archTest가 NO-SOURCE로 건너뛰어 계층 규칙 검사가 조용히 빠진다.
// 도메인 코드가 있는데 규칙 테스트 파일이 없거나 LayerRules를 돌리지 않으면 실패시킨다
val mainJava = sourceSets.main.get().java.sourceDirectories
val layerArchitectureTest = layout.projectDirectory
    .file("src/test/java/com/bbcc/kidly/architecture/LayerArchitectureTest.java").asFile
val checkLayerArchitectureTest = tasks.register("checkLayerArchitectureTest") {
    description = "도메인 코드가 있으면 계층 규칙 테스트(LayerArchitectureTest)가 있는지 확인한다"
    group = "verification"
    doLast {
        val runsLayerRules = layerArchitectureTest.exists() &&
            layerArchitectureTest.readText().contains("ArchTests.in(LayerRules.class)")
        if (!mainJava.asFileTree.isEmpty && !runsLayerRules) {
            throw GradleException(
                "$modulePath 에 계층 규칙 테스트가 없다(LayerArchitectureTest가 ArchTests.in(LayerRules.class)를 돌려야 한다). " +
                    "domains/member의 LayerArchitectureTest.java를 같은 경로로 복사한다")
        }
    }
}
tasks.named("archTest") {
    dependsOn(checkLayerArchitectureTest)
}
