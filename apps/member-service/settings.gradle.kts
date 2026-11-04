// 회원 서비스는 저장소 루트 빌드와 따로 빌드한다. 이 폴더만으로 빌드 · 테스트 · 패키징이 된다
plugins {
    // 로컬에 JDK 25가 없으면 toolchain이 자동으로 내려받는다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

rootProject.name = "member-service"

include("support", "auth", "member", "seller")

// 서비스 사이 계약(com.bbcc:shared)은 저장소의 소스를 그대로 쓴다
includeBuild("../../shared")
