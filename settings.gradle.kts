// 공통 빌드 설정(convention plugin)은 build-logic에 두고 included build로 가져온다
pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // 로컬에 JDK 25가 없으면 toolchain이 자동으로 내려받는다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// 저장소는 여기서만 선언한다. 모듈 빌드 파일이 저장소를 따로 넣으면 실패한다
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

rootProject.name = "kidly"

include("shared")
include("common")
// ArchUnit 공통 규칙. 도메인 모듈과 앱의 archTest가 쓴다
include("architecture")
include("domains:member")
include("domains:seller")
include("domains:childprofile")
include("domains:setting")
include("domains:notification")
include("domains:product")
include("domains:recommend")
include("domains:order")
include("domains:payment")
include("domains:settlement")
include("apps:kidly-app")
