# 회원 서비스

`apps/member-service`는 저장소 루트 빌드와 따로 빌드하는 Gradle 빌드다. 자체 `settings.gradle.kts`, `gradle/libs.versions.toml`, wrapper, 로컬 JWT 키를 가진다. 저장소에서 가져오는 것은 서비스 사이 계약 `shared`와 팀 공통 코드 스타일(루트 `config/checkstyle`)이고, `shared`는 `includeBuild("../../shared")`로 소스를 그대로 쓴다(의존 좌표 `com.bbcc:shared`).

- 실행 앱(이 폴더의 `src`): `member.config`에 실행 설정과 빈 조립, `member.web`에 필터 · 오류 응답 처리 · 로그인 인자 resolver를 둔다. 통합·기능 테스트도 앱에서 실행한다.
- auth/member/seller/childprofile: 도메인 코드, 리소스, 단위 테스트, fixture. 서로 직접 의존하지 않고 shared 계약을 사용한다.
- support: 오류 타입, 기본 엔티티, 인증 인자, 페이지 응답만 둔다. Refresh Token 쿠키는 auth가 소유한다. 테스트 fixture에는 로그인 헤더(`TestMembers` · `TestAdmins`)와 도메인 모듈의 계층 규칙(`LayerRules`)이 있다.
- 공통 빌드 설정(Java 25, Checkstyle, Lombok, Boot BOM, 테스트 태스크)은 루트 `build.gradle.kts`의 `allprojects` · `subprojects`에 있다. 각 모듈의 `build.gradle.kts`는 실제로 쓰는 의존성만 선언한다.

```sh
cd apps/member-service
./gradlew test archTest                        # 단위 테스트 · 계층 규칙 (Docker 불필요)
./gradlew integrationTest functionalTest       # Docker 필요
./gradlew bootRun                              # 8081, 기본 프로필 local
./gradlew bootJar
```

Gradle 버전은 이 폴더의 wrapper가 정하므로 이 폴더의 `./gradlew`로 실행한다. 루트에서 `./gradlew -p apps/member-service`로 실행하면 루트 wrapper의 Gradle 버전이 쓰인다. 루트의 `./gradlew test`는 회원 서비스를 포함하지 않는다. IntelliJ에서는 이 폴더를 Gradle 프로젝트로 따로 연결(Link Gradle Project)해야 `MemberServiceApplication` 실행 구성이 동작한다.

설정의 상대 경로(`file:config/jwt/...`, `.env`)는 이 폴더 기준이다. 로컬에서 카카오 로그인을 쓰려면 `KAKAO_*` 값이 든 `.env`를 이 폴더에 둔다. `config/jwt/local-*.pem`은 저장소 루트의 같은 파일과 같아야 Gateway가 토큰을 검증한다.

도메인 간 직접 참조와 support의 도메인 참조는 루트 `build.gradle.kts`가 설정 단계에서 막는다. 이 빌드에는 `:common`이 없어 공통 모듈이 다시 들어올 수 없다.

모듈 경계는 개별 빌드·캐시 단위이며 독립 배포를 의미하지 않는다. auth/member 사이 트랜잭션 계약은 추출 시 별도 해소해야 한다. 기존 이벤트의 패키지·직렬화 계약과 마이그레이션 동작은 유지한다.
