plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

// convention plugin이 id로 적용할 수 있게 지금 쓰는 플러그인과 같은 버전을 넣는다
dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.1")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}
