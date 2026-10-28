plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

// convention plugin이 plugins { id(...) }로 적용하는 Gradle 플러그인. 버전은 catalog에 있고,
// Boot 플러그인 버전이 곧 Boot BOM 버전이라 모든 모듈의 Spring 의존 버전이 여기서 정해진다
dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
}
