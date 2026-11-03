# 인증 도메인 (#686)

원래 회원 도메인 안에 있던 인증을 떼어 낸 도메인. 회원 밖 주체(관리자 · 자녀)의 로그인을 붙일 바탕이다.
소유: 소셜 계정 연결, Refresh Token, Access Token 발급 · 무효화.

## 가입 흐름 (`SocialLoginService`)
- 소셜 로그인이 처음이면 세 트랜잭션으로 가입시킨다: 선점(소셜 계정 행 하나를 먼저 만든다) → `MemberAccounts.register`로 회원 가입(가입 키 `social-account:{소셜계정 id}`) → 연결(`SocialAccount.link`)
- 각 단계가 멱등이라 어느 지점에서 죽어도 다음 로그인이 같은 끝 상태로 모은다
  - 선점: 유니크 충돌(동시 첫 로그인)이면 이미 있는 행을 읽어 같은 가입 키로 간다
  - 가입: `MemberAccounts.register`가 가입 키로 이미 있는 회원을 돌려준다(회원 쪽이 멱등하게 처리한다)
  - 연결: 선점 행이 이미 회원에 연결돼 있으면 선점 단계로 가지 않고 그 회원으로 바로 로그인한다
- Withdrawn 자가 치유: 연결된 회원이 이미 탈퇴 상태면(`MemberWithdrawnEventListener`가 아직 연결을 못 지운 사이) 그 자리에서 연결을 지우고 새 회원으로 가입시킨다
- 탈퇴 리스너(`in/MemberWithdrawnEventListener`): `member.withdrawn`을 같은 도메인처럼 Spring 이벤트(`@DomainEventListener`)로 받아 그 회원의 소셜 계정 연결을 모두 끊는다. 같은 소셜 계정으로 다시 로그인하면 새 회원이 된다
- 클래스에 트랜잭션을 걸지 않는다. 유니크 충돌 뒤 다시 조회해야 하고, 소셜 제공자 호출이 트랜잭션 밖이라야 제공자가 느려도 DB 커넥션을 붙잡지 않는다

## Access Token 무효화 (`AccessTokenRevocationService`, `shared.auth.out.MemberTokenRevoker`)
- 호출자 트랜잭션 안에서만 부른다(`@Transactional(propagation = MANDATORY)`). 호출자가 커밋해야 효력이 생기고 롤백되면 아무 일도 없다
- 회원은 `MemberTokenRevoker.revoke(memberId, revokedAt)`로 부른다(탈퇴 · 관리자 정지 · 권한 회수 · 판매자 정지). 인증 안(Refresh Token 재사용 감지)은 `revoke(PrincipalId, revokedAt)`를 바로 부른다
- `PrincipalType`은 지금 `MEMBER`뿐이다. 관리자 · 자녀 로그인이 생기면 값을 더한다
- `AuthTokensRevokedEvent`(`auth.tokens-revoked`)를 커밋 뒤 바로 Kafka로 보낸다(`AuthEventRelay` → `kidly.auth.events`). 아웃박스가 아니다: 잃으면 Gateway가 그 주체의 Access Token을 만료(최대 30분)까지 통과시키지만 그 뒤로는 피해가 없다
- Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 전송 대기열(`auth-kafka-sender` 스레드 하나)에 넣는다. 설정은 `out/kafka/AuthKafkaConfig`(토픽 파티션 3 · 복제 1, 전용 템플릿 `max.block.ms` 3초)
- Gateway가 받아 (주체 종류, 주체 ID)별 무효화 목록을 채운다. 자세한 수신 쪽 규칙은 `apps/gateway/CLAUDE.md`

## 다른 도메인과의 계약
- `shared.member.out.MemberAccounts`(회원 소유): 가입 키로 회원을 만들고(`register`) 로그인 가능 여부를 읽는다(`findLoginState`)
- `shared.auth.out.MemberTokenRevoker`(인증 소유): 회원이 Access Token 무효화를 요청하는 계약
- `shared.auth.out.SocialAccountFinder`(인증 소유): 회원에 연결된 소셜 제공자 이름을 읽는다(`SocialLoginService`가 구현)
- 쿠키(Refresh Token)는 `common`의 `global.security.RefreshTokenCookies`다. 인증(`AuthController`)과 회원(탈퇴) 양쪽이 같이 쓴다
- 설정은 `auth.yml`(`jwt.*` · `refresh-token.*` · `kakao.*`)

## 그 밖
- `RefreshTokenCleaner`: 만료된 Refresh Token을 하루 한 번(04:00 KST) 지운다. 재발급할 때도 만료를 확인하므로 정리가 늦어도 동작은 같다
- `DevTokenService` · `DevTokenController`: `@DevOnly`. 운영에는 등록되지 않는다
