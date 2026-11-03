# 인증 도메인 (#686)

원래 회원 도메인 안에 있던 인증을 떼어 낸 도메인. 관리자 로그인이 여기 있고 자녀는 같은 틀로 붙는다.
소유: 소셜 계정 연결, 관리자 계정, Refresh Token, Access Token 발급 · 무효화.

로그인 주체는 `shared.auth.PrincipalId`(종류 + ID) 하나다. Access Token `sub`는 회원 `"42"`, 관리자 `"admin:7"`(`PrincipalId.toSubject`).
종류별 갈래는 `PrincipalType` · `TokenSubject`에 대한 완전한 switch다: `JwtAccessTokenIssuer` 클레임, `RefreshTokenService.currentSubject`,
`common`의 `RefreshTokenCookies`. 자녀를 더하면 이 셋이 컴파일 오류로 드러난다.

## 가입 흐름 (`SocialLoginService`)
- 소셜 로그인이 처음이면 세 트랜잭션으로 가입시킨다: 선점(소셜 계정 행 하나를 먼저 만든다) → `MemberAccounts.register`로 회원 가입(가입 키 `social-account:{소셜계정 id}`) → 연결(`SocialAccount.link`)
- 각 단계가 멱등이라 어느 지점에서 죽어도 다음 로그인이 같은 끝 상태로 모은다
  - 선점: 유니크 충돌(동시 첫 로그인)이면 이미 있는 행을 읽어 같은 가입 키로 간다
  - 가입: `MemberAccounts.register`가 가입 키로 이미 있는 회원을 돌려준다(회원 쪽이 멱등하게 처리한다)
  - 연결: 선점 행이 이미 회원에 연결돼 있으면 선점 단계로 가지 않고 그 회원으로 바로 로그인한다
- Withdrawn 자가 치유: 연결된 회원이 이미 탈퇴 상태면(`MemberWithdrawnEventListener`가 아직 연결을 못 지운 사이) 그 자리에서 연결을 지우고 새 회원으로 가입시킨다
- 탈퇴 리스너(`in/MemberWithdrawnEventListener`): `member.withdrawn`을 같은 도메인처럼 Spring 이벤트(`@DomainEventListener`)로 받아 그 회원의 소셜 계정 연결을 모두 끊는다. 같은 소셜 계정으로 다시 로그인하면 새 회원이 된다
- 클래스에 트랜잭션을 걸지 않는다. 유니크 충돌 뒤 다시 조회해야 하고, 소셜 제공자 호출이 트랜잭션 밖이라야 제공자가 느려도 DB 커넥션을 붙잡지 않는다

## 관리자 계정 · 로그인 (#736)
- 계정은 `auth.admin_account`(`AdminAccount`): 이메일(앞뒤 공백 제거 · 소문자, UNIQUE) · BCrypt 해시(`out/AuthPasswordConfig`의 `authPasswordEncoder`) · `ACTIVE`/`DISABLED` · 만든 관리자 ID(`created_by`, 첫 관리자만 비어 있다)
- 관리자 생성은 `POST /api/v1/admin/admins`, 비활성화는 `POST /api/v1/admin/admins/{adminId}/disable`이다. 둘 다 `@LoginAdmin`을 받는다. 생성 시 비밀번호는 12자 이상 · UTF-8 72바이트 이하다
- 비활성화는 관리자 행을 잠근 뒤 시각과 실행자 ID를 남기고 Refresh Token을 삭제하며 Access Token 무효화를 발행한다. 자기 자신은 비활성화할 수 없고 이미 비활성화된 계정에는 무효화를 다시 발행하지 않는다
- 생성 · 비활성화는 호출한 관리자의 행도 잠그고 ACTIVE가 아니면 401이다. 비활성화된 관리자의 Access Token은 무효화가 Gateway에 닿기 전까지 잠깐 통과하기 때문이다. 비활성화는 두 행을 ID가 작은 쪽부터 잠가, 두 관리자가 서로를 동시에 비활성화해도 늦은 쪽이 막혀 활성 관리자가 0명이 되지 않는다
- API는 `/api/v1/auth/admin/{login,reissue,logout}`(`AdminAuthController`). `/api/v1/admin/**` 밑은 Gateway가 관리자 주체를 요구해 로그인 전 요청이 막히므로 `/api/v1/auth` 밑에 둔다
- 로그인(`AdminAuthService`): 한 트랜잭션에서 이메일로 행을 잠그고(`findByEmailForUpdate`) → 비밀번호 → ACTIVE → Refresh 발급 → Access 발급. 비활성화와 엇갈려 토큰이 남는 창이 없다. 없는 이메일 · 틀린 비밀번호 · DISABLED는 모두 같은 401(`INVALID_ADMIN_CREDENTIALS`)이고, 없는 이메일에도 더미 해시로 `matches`를 한 번 돌려 응답 시간으로 계정 유무가 드러나지 않게 한다. `authenticate`와 `issue`가 나뉘어 있어 MFA는 그 사이에 끼운다. 로그인 시도 제한은 아직 없다
- 관리자 Access Token은 `sub` · `iat` · `exp`만 담는다(`TokenSubject.Admin`). 관리자는 한 종류라 역할이 없다
- Refresh Token 행의 키는 `(principal_type, principal_id)`(`RefreshTokenId`)라 같은 숫자 ID의 회원 · 관리자 토큰이 따로 산다. `reissue(expected, token)` · `revoke(expected, token)`은 행의 종류가 엔드포인트의 종류와 다르면 거절 · 무시한다(회원 쿠키 값으로 관리자 재발급, 그 반대). 재발급은 `currentSubject`가 종류별로 "지금 토큰을 줘도 되는가"를 본다: 회원은 `MemberAccounts.findLoginState`가 Active, 관리자는 행을 잠근 뒤(`findByIdForUpdate`) ACTIVE
- 쿠키는 주체마다 다르다: 회원 `refresh_token`(Path `/api/v1/auth`, 바이트 그대로), 관리자 `admin_refresh_token`(Path `/api/v1/auth/admin`). 같은 브라우저에 둘 다 로그인해도 서로 덮이지 않는다
- 첫 관리자 부트스트랩(`in/AdminBootstrapRunner` → `AdminBootstrapService`): 기동 때 `kidly.auth.admin.bootstrap.email` · `.password`(`KIDLY_ADMIN_BOOTSTRAP_EMAIL` · `KIDLY_ADMIN_BOOTSTRAP_PASSWORD`, local 기본값 `admin@kidly.local` / `local-admin-password`)로 관리자가 한 명도 없을 때만 만든다. `INSERT … WHERE NOT EXISTS … ON CONFLICT DO NOTHING`과 `created_by IS NULL` 부분 유니크 인덱스라 두 번 돌아도, 두 인스턴스가 다른 이메일로 동시에 떠도 한 행이다. 관리자가 생긴 뒤에는 설정을 무시한다(설정이 영구 비밀번호 재설정 통로가 되지 않게). 이메일이 비면 아무것도 하지 않고, 이메일만 있고 비밀번호가 비면 기동에 실패한다. 비밀번호는 12자 이상 · UTF-8 72바이트 이하(`AdminPassword`). 이메일 · 비밀번호는 로그에 쓰지 않는다. test 프로필은 값이 비어 있어 돌지 않는다

## Access Token 무효화 (`AccessTokenRevocationService`, `shared.auth.out.MemberTokenRevoker`)
- 호출자 트랜잭션 안에서만 부른다(`@Transactional(propagation = MANDATORY)`). 호출자가 커밋해야 효력이 생기고 롤백되면 아무 일도 없다
- 회원은 `MemberTokenRevoker.revoke(memberId, revokedAt)`로 부른다(탈퇴 · 관리자 정지 · 권한 회수 · 판매자 정지). 인증 안(Refresh Token 재사용 감지)은 `revoke(PrincipalId, revokedAt)`를 바로 부른다
- `PrincipalType`은 `MEMBER` · `ADMIN`이다. 자녀 로그인이 생기면 값을 더한다(받는 쪽인 Gateway가 먼저)
- `AuthTokensRevokedEvent`(`auth.tokens-revoked`)를 커밋 뒤 바로 Kafka로 보낸다(`AuthEventRelay` → `kidly.auth.events`). 아웃박스가 아니다: 잃으면 Gateway가 그 주체의 Access Token을 만료(최대 30분)까지 통과시키지만 그 뒤로는 피해가 없다
- Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 전송 대기열(`auth-kafka-sender` 스레드 하나)에 넣는다. 설정은 `out/kafka/AuthKafkaConfig`(토픽 파티션 3 · 복제 1, 전용 템플릿 `max.block.ms` 3초)
- Gateway가 받아 (주체 종류, 주체 ID)별 무효화 목록을 채운다. 자세한 수신 쪽 규칙은 `apps/gateway/CLAUDE.md`

## 다른 도메인과의 계약
- `shared.member.out.MemberAccounts`(회원 소유): 가입 키로 회원을 만들고(`register`) 로그인 가능 여부를 읽는다(`findLoginState`)
- `shared.auth.out.MemberTokenRevoker`(인증 소유): 회원이 Access Token 무효화를 요청하는 계약
- `shared.auth.out.SocialAccountFinder`(인증 소유): 회원에 연결된 소셜 제공자 이름을 읽는다(`SocialLoginService`가 구현)
- 쿠키(Refresh Token)는 `common`의 `global.security.RefreshTokenCookies`다. 주체 종류를 받는다. 인증(`AuthController` · `AdminAuthController`)과 회원(탈퇴) 양쪽이 같이 쓴다
- 설정은 `auth.yml`(`jwt.*` · `refresh-token.*` · `kakao.*` · `kidly.auth.admin.bootstrap.*`)

## 그 밖
- `RefreshTokenCleaner`: 만료된 Refresh Token을 하루 한 번(04:00 KST) 지운다. 재발급할 때도 만료를 확인하므로 정리가 늦어도 동작은 같다
- `DevTokenService` · `DevTokenController`: `@DevOnly`. 운영에는 등록되지 않는다
