// 루트와 같은 version catalog를 읽어 플러그인 버전도 한 곳에서 관리한다
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
