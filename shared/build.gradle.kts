// 도메인 사이 계약(이벤트 · DTO). JDK만 쓴다
plugins {
    id("kidly.library")
}

// 이 폴더에서 독립 빌드로 열 때(settings 디렉터리가 shared)도 루트의 공통 규칙을 쓴다
checkstyle {
    configDirectory = layout.projectDirectory.dir("../config/checkstyle")
}
