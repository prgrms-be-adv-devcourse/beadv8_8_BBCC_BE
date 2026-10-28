plugins {
    // 로컬에 JDK 25가 없으면 toolchain이 자동으로 내려받는다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kidly"

include("shared")
include("common")
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
