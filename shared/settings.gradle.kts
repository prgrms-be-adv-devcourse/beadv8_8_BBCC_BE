// 루트 빌드 밖(apps/member-service)에서 includeBuild로 가져갈 때 쓰는 설정. 루트 빌드에서는 읽지 않는다
pluginManagement {
    includeBuild("../build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "shared"
