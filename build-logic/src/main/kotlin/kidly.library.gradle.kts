// 다른 모듈이 가져다 쓰는 라이브러리 모듈. api로 공개 시그니처의 의존을 전달할 수 있게 java-library를 쓴다
plugins {
    `java-library`
    id("kidly.java-conventions")
}
