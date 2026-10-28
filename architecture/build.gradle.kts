// ArchUnit 공통 규칙(com.bbcc.kidly.architecture). 도메인 모듈과 앱의 테스트 클래스패스에만 들어간다
plugins {
    id("kidly.library")
}

dependencies {
    // 규칙 · ImportOption의 공개 시그니처에 나오고, 쓰는 쪽 테스트가 @ArchTest로 실행한다
    api(libs.archunit.junit5)
}
