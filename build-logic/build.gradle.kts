plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

// convention plugin이 id로 적용할 수 있게 지금 쓰는 플러그인과 같은 버전을 넣는다
dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.dependency.management.plugin)
}
