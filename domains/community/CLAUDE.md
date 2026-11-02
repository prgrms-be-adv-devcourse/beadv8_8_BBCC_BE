# 커뮤니티 도메인 설계 규칙

커뮤니티 모듈(리뷰 · 찜 컨텍스트) 안의 설계 규칙이다. 도메인 경계 · `shared` 계약 · Kafka 토픽/키/그룹 · 묻고 바꿀 것은 루트 `CLAUDE.md`를 따르고, 여기와 부딪히면 루트가 우선이다.

## 1. 컨텍스트 · 계층과 포트
- 이 모듈은 컨텍스트 둘이다: `boundedcontext.review`(중심: 리뷰 · 작성 자격 · 상품 복사본 · 회원 탈퇴 수신) · `boundedcontext.favorite`(찜). 저장은 모두 `community` 스키마다
- 컨텍스트 사이는 in · out으로만 잇는다(`api` 패키지는 두지 않는다). 받는 쪽은 들어오는 포트를 자기 `app.port.in`에 두고 app이 구현한다. 부르는 쪽은 필요한 것을 자기 `app.port`에 자기 말로 두고, `out.{상대}` 어댑터가 상대 `app.port.in`을 불러 값을 옮겨 담는다. 다른 컨텍스트를 아는 것은 이 어댑터뿐이다(app · domain · in은 모른다). 예: 찜은 상품 정보를 review의 `app.port.in`으로 읽는다
- `domain`: 업무 규칙 · 불변식(엔티티 · 값, JPA 엔티티 겸용). JPA 표준 어노테이션만 쓰고 Spring · `common` · 로그를 쓰지 않는다. 업무 판단은 domain 메서드가 하고 app은 순서만 조율한다. 불변식 위반은 도메인 예외다
- `app`: 유스케이스 서비스. 오류 코드는 컨텍스트마다 `ErrorCode`를 구현한 enum(`ReviewErrorCode` · `FavoriteErrorCode`)과 `BusinessException`
- `app.port`: 나가는 것(저장소 · 다른 컨텍스트)의 인터페이스, `app.port.in`: 다른 컨텍스트가 들어오는 인터페이스. 둘 다 최상위는 인터페이스만 두고, 주고받는 값은 그 인터페이스 안에 둔다(값은 record, 종류는 enum). 나가는 포트 구현은 `out`(저장소는 Spring Data `Jpa…Repository`가 포트를 상속), 들어오는 포트 구현은 app
- `in`: `web`(컨트롤러 · `dto`, 관리자 API가 생기면 `web.admin`) · `event`(이벤트 수신 · 매퍼) · `config`(Kafka 리스너 팩토리 · DLT). `domain`과 `out`을 쓰지 않는다
- `out`: `persistence`(Spring Data JPA 저장소만) · `config`(설정 값) · `store`(Spring Data 저장소 조각 인터페이스 · 구현. `persistence` 저장소가 상속한다. 예: 새 행만 넣는 `ReplicaProductInsert`) · `{상대 컨텍스트}`(어댑터). 바로 아래 하위 패키지는 이 넷만 둔다(`CommunityPackageLayoutArchitectureTest`)
- 설정은 계층마다 `config` 한 곳에 둔다(컨텍스트 바로 아래에 새 폴더를 만들지 않는다): 스프링 설정(`@Configuration`)은 `in.config` · `out.config`, 설정 값(`@ConfigurationProperties`)은 app · `in.config` · `out.config`
- 한 앱 안에서 빈 · 엔티티 이름이 다른 도메인과 겹치면 기동이 실패한다: 다른 도메인에 같은 이름이 있을 수 있는 클래스(`…EventListener` · `SpringEventReceiver` · `EventGuard` 등)는 `Community` 접두어를 붙이고, 엔티티는 `@Entity(name = "Community…")`로 이름을 정한다
- 검사: 계층 규칙은 `LayerArchitectureTest`(`LayerRules`), 컨텍스트 사이 규칙(내 out → 상대 `app.port.in`만)은 `CommunityContextArchitectureTest`, 패키지 배치는 `CommunityPackageLayoutArchitectureTest`. 컨텍스트 루트의 `package-info`는 `@NullMarked`로 클래스를 남겨 뼈대만 있을 때도 검사 대상이 비지 않게 한다

## 2. 이벤트 수신
- 받는 길은 스위치 `kidly.community.kafka.consumer.enabled` 하나로 고른다: `true`(기본)면 Kafka 리스너, `false`면 Spring 이벤트. 처리 코드(`…EventListener`)는 같다
- Kafka 리스너는 전용 팩토리 `communityKafkaListenerContainerFactory`(`in.config`)만 쓴다: 구독 그룹 `kidly.community`, 한 건씩 완료, 실패하면 1초 간격 3번 재시도 뒤 `{토픽}.community.dlt`, 다시 해도 같은 예외 · 읽기 실패는 바로 DLT. 팩토리 외의 Kafka 객체(템플릿 · 에러 처리기)는 이 구성 코드 안에서 만들고 빈으로 등록하지 않는다
- DLT 토픽은 파티션 1개이고, 보낼 때는 원래 파티션과 상관없이 0번으로 보낸다. 도메인 이벤트 토픽의 파티션 3 규칙과 별개다
- 같은 이벤트가 다시 와도 결과는 1회분이다: 복사본(상품 등)은 저장된 `occurredAt`보다 늦은 이벤트만 반영한다
- 필수 값이 없으면 `CommunityEventGuard`로 WARN 한 줄을 남기고 무시한다
- 로그 추적(MDC): Kafka 수신은 메시지 하나를 처리하는 동안 eventId · eventType · `correlationId`(= 키)를 MDC에 넣고, 끝나면(예외 포함) 이전 값으로 되돌린다
- 로그는 결과 하나에 한 줄이고, 본문 · 예외 메시지 · 개인정보를 넣지 않는다(eventType · eventId · 키 · ID · 예외 종류만)

## 3. 저장 · 조회
- 카운터(리뷰 수 · 평점 합 · 찜 수 등)는 집계 행을 원자적 UPDATE(`set count = count + 1`)로 바꾸고, 동시성 테스트로 1회분씩 쌓이는지 확인한다. 읽고 더해 저장하지 않는다
- 중복은 DB 유일 제약으로 막는다: (회원, 상품) 찜, (주문상품) 리뷰
- 수신으로 처음 보는 행을 만들 때는 먼저 조회하고, 저장은 따로 연 트랜잭션(`ReviewSeparateTransaction`)에서 바로 반영한다. ID를 직접 넣는 엔티티(상품 복사본)는 Spring Data `save`가 merge가 되어 이미 커밋된 행을 덮어쓰므로, 새 행은 persist만 하는 저장소 조각(`out.store.ReplicaProductInsert`)으로 넣는다. 동시 수신으로 그 행의 유일 제약(SQLState 23505 + 제약 이름)에 막히고 다시 조회해 행이 있으면 "이미 있음"으로 수렴한다(복사본은 이어서 늦을 때만 덮어쓰기). 그 밖의 위반은 던진다(재시도 → DLT). 로그에 값을 남기지 않는다
- 목록은 커서 페이지네이션 + 인덱스, 쿼리 수 테스트로 N+1을 막는다
- 마이그레이션은 `src/main/resources/db/migration/community/V{yyyyMMddHHmm}__community_{설명}.sql`, 추가만(이미 적용된 파일은 고치지 않는다)

## 4. 상품 복사본 · 기존 상품 채우기
- 상품 복사본(`review.domain.ReplicaProduct`, 표 `community.replica_product`)은 상품당 1행이고 `product.changed`의 지금 값을 덮어쓴다. 리뷰 · 찜 화면의 상품 이름 · 썸네일 · 표시 상태를 여기서 읽는다
- 표시 상태(`DisplayStatus`): 품절은 팔고 있지만 재고가 없는 것, 판매 종료는 더 이상 팔지 않는 것이다
  - 판매 종료(`SALE_ENDED`): 상품이 `STOPPED` · `SALE_PENDING` · 모르는 값(null), 또는 `ON_SALE`이지만 판매 중(`ON_SALE`) 옵션이 0개
  - 품절(`SOLD_OUT`): `ON_SALE`이고 판매 중 옵션이 1개 이상이며 그 옵션이 모두 품절(판매 중지 · 모르는 상태 옵션은 보지 않는다)
  - 판매 중(`ON_SALE`): 그 밖(판매 중 옵션 하나라도 재고가 있다)
- 기존 상품 채우기: 복사본은 커뮤니티가 받기 시작한 뒤의 `product.changed`로만 채워지므로, 커뮤니티를 처음 배포한 뒤 환경마다 한 번 상품 도메인의 관리자 API `POST /api/v1/admin/products/changed-events`를 호출한다. 판매를 시작한 상품 전체를 `ProductChangedEvent`로 다시 보낸다
  - 판매 시작 전 상품은 보내지 않으므로 복사본에 없다. 복사본에 없는 상품은 찜을 거절한다
  - 같은 이벤트를 다른 도메인(주문 · 추천 등)도 다시 받는다. 값이 그대로라 덮어쓰기만 일어난다

## 5. 지금 넣지 않는 것
- 상태 전이 결과 타입 · `@Version` 낙관적 잠금: 리뷰 · 찜 · 복사본은 상태가 단순하고, 같은 행을 동시에 바꾸는 경쟁은 유일 제약 · 원자적 UPDATE · `occurredAt` 비교로 막는다
- 처리 실패 보관 테이블 · DLT 보관 · 관리자 재처리: 수신 실패는 재시도 뒤 DLT로 충분하다. 복사본 · 자격은 같은 이벤트를 다시 받으면 1회분으로 맞춰진다
- 아웃박스 · 릴레이 스케줄러: 커뮤니티가 이벤트를 발행하지 않는 동안은 필요 없다
- 필요해지면 이 문서를 먼저 고치고 넣는다

## 6. 테스트
- 단위 테스트는 `test`, 스프링 + Testcontainers는 태그로 나눠 `integrationTest`(`@IntegrationTest`) · `functionalTest`(`@FunctionalTest`)로 이 모듈에서 돈다. 규칙 테스트는 `archTest`
- 주석 · 컨테이너 · 정리는 모듈 공통 `boundedcontext.support`(앱의 `com.bbcc.kidly.support`와 다른 패키지)를 쓰고, 테스트 앱은 `CommunityTestApplication`(리뷰 · 찜 컨텍스트 · `common`만)이다. 테스트가 끝나면 `community` 스키마의 표를 비운다(`CommunityDatabaseCleanerExtension`)
- `@IntegrationTest` · `@FunctionalTest`를 쓰는 테스트 클래스에 개별 설정(`@MockitoBean`, `@TestPropertySource` 등)을 붙이지 않는다(컨텍스트가 새로 뜬다)
- 커뮤니티 테스트는 다른 도메인 코드를 쓰지 않는다(`CommunityTestIndependenceTest`). 다른 도메인이 보내던 이벤트는 테스트가 직접 넣는다
- 쓰기 입력마다 "두 번 → 1회분" 테스트, 카운터는 동시 호출 테스트를 둔다. 경계(아래 · 정확히 · 위)를 확인하고, 시간은 now 기준 상대값으로 쓴다
