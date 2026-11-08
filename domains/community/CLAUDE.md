# 커뮤니티 도메인 설계 규칙

커뮤니티 모듈(리뷰 · 찜 컨텍스트) 안의 설계 규칙이다. 도메인 경계 · `shared` 계약 · Kafka 토픽/키/그룹 · 묻고 바꿀 것은 루트 `CLAUDE.md`를 따르고, 여기와 부딪히면 루트가 우선이다.

## 1. 컨텍스트 · 계층과 포트
- 이 모듈은 컨텍스트 둘이다: `boundedcontext.review`(중심: 리뷰 · 작성 자격 · 상품 복사본 · 회원 탈퇴 수신) · `boundedcontext.favorite`(찜). 저장은 모두 `community` 스키마다
- 컨텍스트 사이는 in · out으로만 잇는다(`api` 패키지는 두지 않는다). 받는 쪽은 들어오는 포트를 자기 `app.port.in`에 두고 app이 구현한다. 부르는 쪽은 필요한 것을 자기 `app.port`에 자기 말로 두고, `out.{상대}` 어댑터가 상대 `app.port.in`을 불러 값을 옮겨 담는다. 다른 컨텍스트를 아는 것은 이 어댑터뿐이다(app · domain · in은 모른다). 예: 찜은 상품 정보를 review의 `app.port.in.ProductCatalog`(상품 ID 묶음 → 이름 · 썸네일 · 표시 상태, 한 쿼리)로 읽고, 찜 쪽 `app.port.FavoriteProducts`를 `favorite.out.review` 어댑터가 구현한다. 시각 · 커서 · size 도우미처럼 같은 규칙의 코드도 컨텍스트마다 따로 둔다(컨텍스트 사이 참조 금지)
- `domain`: 업무 규칙 · 불변식(엔티티 · 값, JPA 엔티티 겸용). JPA 표준 어노테이션만 쓰고 Spring · `common` · 로그를 쓰지 않는다. 업무 판단은 domain 메서드가 하고 app은 순서만 조율한다. 불변식 위반은 도메인 예외다
- `app`: 유스케이스 서비스. 오류 코드는 컨텍스트마다 `ErrorCode`를 구현한 enum(`ReviewErrorCode` · `FavoriteErrorCode`)과 `BusinessException`
- `app.port`: 나가는 것(저장소 · 다른 컨텍스트)의 인터페이스, `app.port.in`: 다른 컨텍스트가 들어오는 인터페이스. 둘 다 최상위는 인터페이스만 두고, 주고받는 값은 그 인터페이스 안에 둔다(값은 record, 종류는 enum). 나가는 포트 구현은 `out`(저장소는 Spring Data `Jpa…Repository`가 포트를 상속), 들어오는 포트 구현은 app
- `in`: `web`(컨트롤러 · `dto`, 관리자 API는 `web.admin`) · `event`(이벤트 수신 · 매퍼) · `scheduler`(주기 실행, app 서비스만 부른다) · `mcp`(운영 조회 MCP 창구, app 조회 서비스만 부른다) · `config`(Kafka 리스너 팩토리 · DLT). `domain`과 `out`을 쓰지 않는다
- `out`: `persistence`(Spring Data JPA 저장소만) · `config`(설정 값) · `store`(Spring Data 저장소 조각 · 기술 보관 행/저장소 어댑터. `persistence` 저장소가 상속한다. 예: 새 행만 넣는 `ReplicaProductInsert`) · `storage`(DB 밖 파일 저장소 어댑터. 예: 리뷰 사진 업로드 주소를 서명하는 `S3ReviewImageStorage`) · `moderation`(리뷰 내용 검사기 어댑터: 금칙어 · OpenAI 호환 AI와 이어 붙이기) · `{상대 컨텍스트}`(어댑터). 바로 아래 하위 패키지는 이 여섯만 둔다(`CommunityPackageLayoutArchitectureTest`)
- 설정은 계층마다 `config` 한 곳에 둔다(컨텍스트 바로 아래에 새 폴더를 만들지 않는다): 스프링 설정(`@Configuration`)은 `in.config` · `out.config`, 설정 값(`@ConfigurationProperties`)은 app · `in.config` · `out.config`
- 한 앱 안에서 빈 · 엔티티 이름이 다른 도메인과 겹치면 기동이 실패한다: 다른 도메인에 같은 이름이 있을 수 있는 클래스(`…EventListener` · `SpringEventReceiver` · `EventGuard` 등)는 `Community` 접두어를 붙이고, 엔티티는 `@Entity(name = "Community…")`로 이름을 정한다
- 검사: 계층 규칙은 `LayerArchitectureTest`(`LayerRules`), 컨텍스트 사이 규칙(내 out → 상대 `app.port.in`만)은 `CommunityContextArchitectureTest`, 패키지 배치는 `CommunityPackageLayoutArchitectureTest`. 컨텍스트 루트의 `package-info`는 `@NullMarked`로 클래스를 남겨 뼈대만 있을 때도 검사 대상이 비지 않게 한다

## 2. 이벤트 수신
- 받는 길은 스위치 `kidly.community.kafka.consumer.enabled` 하나로 고른다: `true`(기본)면 Kafka 리스너, `false`면 Spring 이벤트. 처리 코드(`…EventListener`)는 같다
- Kafka 리스너는 전용 팩토리 `communityKafkaListenerContainerFactory`(`in.config`)만 쓴다: 구독 그룹 `kidly.community`, 한 건씩 완료, 실패하면 1초 간격 3번 재시도 뒤 `{토픽}.community.dlt`, 다시 해도 같은 예외 · 읽기 실패는 바로 DLT. 팩토리 외의 Kafka 객체(템플릿 · 에러 처리기)는 이 구성 코드 안에서 만들고 빈으로 등록하지 않는다
- DLT 토픽은 파티션 1개이고, 보낼 때는 원래 파티션과 상관없이 0번으로 보낸다. 도메인 이벤트 토픽의 파티션 3 규칙과 별개다
- 상품 복사본 반영(#738)은 상품 ID별 트랜잭션 advisory lock(`ReplicaProducts.lockProduct`, 저장소 조각 `out.store.ReplicaProductLock`)을 최초 조회 전에 잡고 바깥 트랜잭션의 커밋 · 롤백까지 유지한다. 기존 행의 동시 갱신과 최초 생성을 함께 직렬화한다. 최초 INSERT는 기존 `ReviewSeparateTransaction` 경로를 유지한다(이 안에서는 같은 advisory lock을 다시 잡지 않는다).
- 토픽 하나는 리스너 하나가 받는다(같은 토픽 · 같은 그룹에 리스너가 둘이면 메시지를 나눠 가진다). 회원 탈퇴(`member.withdrawn`)는 review가 받아, 찜 정리는 `review.out.favorite` → favorite `app.port.in.MemberFavoritesRemover`로 같은 트랜잭션에서 맡긴다. 리뷰는 남기고 닉네임을 지우며 `author_withdrawn_at`을 처음 한 번만 기록한다(응답은 nickname "탈퇴한 회원" · authorWithdrawn true)
- 같은 이벤트가 다시 와도 결과는 1회분이다: 복사본(상품 등)은 저장된 `occurredAt`보다 늦은 이벤트만 반영한다
- 필수 값이 없으면 `CommunityEventGuard`로 WARN 한 줄을 남기고 무시한다
- 로그 추적(MDC): Kafka 수신은 메시지 하나를 처리하는 동안 eventId · eventType · `correlationId`(= 키)를 MDC에 넣고, 끝나면(예외 포함) 이전 값으로 되돌린다
- 로그는 결과 하나에 한 줄이고, 본문 · 예외 메시지 · 개인정보를 넣지 않는다(eventType · eventId · 키 · ID · 예외 종류만)

## 3. 저장 · 조회
- 카운터(리뷰 수 · 평점 합 · 찜 수 등)는 집계 행을 원자적 UPDATE(`set count = count + 1`)로 바꾸고, 동시성 테스트로 1회분씩 쌓이는지 확인한다. 읽고 더해 저장하지 않는다. 첫 행은 `INSERT … ON CONFLICT DO UPDATE`(증가만)로 만들고, 감소는 있는 행의 UPDATE로 한다(감소를 upsert로 하면 넣으려던 음수 행이 CHECK(0 이상)에 먼저 걸린다). 같은 행(리뷰 등)의 수정 · 삭제가 겹치면 그 행을 select for update로 잡아 증감이 한 번씩만 일어나게 한다
- 중복은 DB 유일 제약으로 막는다: (회원, 상품) 찜, (주문상품) 리뷰
- 리뷰 사이즈 핏(`Fit`: SMALL · TRUE_TO_SIZE · LARGE, 선택)은 구매 사이즈가 있는 주문상품만 받는다. 핏별 수는 `review_stats`의 `fit_*`를 다른 집계와 같은 문장에서 증감한다(작성 +1, 수정 이전 −1 · 새 +1, 삭제 −1). 구매 사이즈(`purchasedSize`)는 리뷰에 따로 저장하지 않고 작성 자격(`review_eligibility.size`)을 조인해 읽는다
- 리뷰 사진: 브라우저가 업로드 주소 발급(`POST /api/v1/reviews/images/upload-urls`)으로 받은 서명 주소로 S3에 직접 올리고, 받은 공개 주소(`imageUrls`)를 리뷰 작성 · 수정에 보낸다. 서명 · 공개 주소는 `app.port.ReviewImageStorage`(구현 `out.storage`)만 알고, domain · app은 S3를 모른다
  - 주소 검사는 보안 경계다: 본인 경로(`{key-prefix}/reviews/{회원 ID}/`) 공개 주소 접두어 + 서버가 만든 이름(소문자 UUID.jpg|png|webp)만 받고, 같은 주소는 한 번만(`ReviewImageUrls`)
  - 업로드 확인(#1030): 형식 · 본인 경로 · 장수 검사 뒤 새로 붙는 주소만 저장소에 실제로 있는지 확인한다(`ReviewImageStorage.findNotUploaded`, 구현은 `out.storage`의 확인 방식: s3 = 동기 S3Client HeadObject(HTTP 구현은 S3 SDK가 가져온 Apache 5 자동 선택) · none = 확인 안 함, 설정 `upload-check`). 없으면 400 `IMAGE_NOT_UPLOADED`, 확인 불가(403 · 5xx · 시간 초과 · 연결 실패)면 503 `IMAGE_STORAGE_UNAVAILABLE`, 둘 다 저장하지 않는다. 외부 호출은 트랜잭션 밖이다: 작성은 저장 트랜잭션 전, 수정은 잠그지 않고 읽어 모든 검사(`Review.checkUpdate`)를 미리 한 뒤 확인하고 행 잠금으로 다시 읽어 한 트랜잭션에서 반영한다. 이미 붙어 있던 주소는 붙일 때 확인했으므로 사이에 다른 수정이 끼어도 다시 보지 않는다. 로그에 주소 · key를 남기지 않는다
  - 설정은 `community.yml`의 `kidly.community.review.image.*`(장수 · 크기는 app `ReviewImageProperties`, 버킷 · 경로 · 서명은 `out.config`). 최대 장수(`max-count`)는 값만 바꿔 조절한다. 로컬 · 테스트 프로필은 가짜 자격 증명이다
  - 사진 행(`review_image`)은 리뷰를 지우면 DB가 함께 지운다(ON DELETE CASCADE). 수정에서 보내면 그 리뷰의 행을 모두 지우고 보낸 순서로 새로 넣는다. 목록은 한 쪽의 리뷰 ID로 사진을 한 번에 읽는다(쪽마다 쿼리 1번 더, 사진 수와 상관없음)
  - S3 객체는 지우지 않는다(리뷰 삭제 · 사진 교체 · 쓰이지 않은 업로드 모두). 정리는 따로 정한다
- 리뷰 내용 검사(#1002): 리뷰는 저장하면 바로 공개(`PENDING`)이고, 스케줄러(`in.scheduler`)가 주기마다 대기 리뷰를 검사해 걸리면 숨긴다(`HIDDEN`, 관리자 판단 대기). 관리자는 숨김 확정(`CONFIRMED_HIDDEN`) · 다시 공개(`RESTORED`)를 한다
  - 검사는 `app.port.ContentModerator` 하나로만 부르고 결과는 커뮤니티 말(PASSED · FLAGGED(이유 분류) · UNDECIDED(원인))이다. 제공자 요청 · 응답 해석 · 분류 대응(Llama Guard S1~S14 → 이유)은 `out.moderation` 어댑터 안에서만 한다. 검사기 조합(`moderators`) · 금칙어 · AI 주소 · 모델 · 키(환경변수) · 시간은 `community.yml` 설정이다. 새 제공자는 `ModerationAdapter` 하나를 더하고 이름을 설정에 넣는다. 모든 어댑터는 같은 계약 테스트(`ContentModeratorContractTest` 상속)를 통과한다
  - 실행: 서버 한 대만(PostgreSQL 세션 advisory lock, 못 잡으면 건너뜀), 검사기 호출은 트랜잭션 밖, 결과 반영은 한 건 = 트랜잭션 하나(`ReviewModerationRecorder`가 행 잠금으로 다시 읽어 버전 · 시도 횟수가 그대로이고 아직 대기일 때만). 판단 못 함(장애 · 시간 초과)은 대기 그대로 시도 +1, 429는 시도 횟수를 올리지 않고 그 주기의 남은 건을 멈춘다. 재시도 상한에 닿으면 대기 목록에서 빠지고 공개 그대로(관리자 목록 `attemptsExhausted`)
  - 글 · 사진이 실제로 바뀐 수정은 버전 +1이고, 공개 상태면 다시 대기(숨김 상태는 그대로). 별점 · 핏만 바뀐 수정은 그대로
  - 집계: 숨김 리뷰는 상품 요약 집계에 들어 있지 않다. 공개 → 숨김이면 그 리뷰 몫(수 · 별점 · 핏)을 빼고, 숨김 → 공개면 그때 값으로 더한다(같은 트랜잭션, 실제로 바뀔 때만). 숨김 리뷰의 수정 · 삭제는 집계를 건드리지 않는다. 숨김 · 복원 · 수정 · 삭제 · 탈퇴 뒤 요약 = 공개 리뷰만 다시 센 값(`ReviewModerationStatsIntegrationTest`)
  - 로그는 리뷰 ID · 결과 · 이유 · 원인만. 키 · 요청/응답 본문 · 리뷰 본문 · 사진 주소를 넣지 않는다
- 운영 조회 MCP 창구(#1005, 읽기 전용): `POST /api/v1/admin/community/mcp`, 스위치 `kidly.community.mcp.enabled`(기본 false) · 목록 상한 `max-results`. 도구는 review_summary · dead_letters · dead_letter_detail · moderation_queue · replica_health
  - 도구 정의(`in.mcp`의 `CommunityMcpTool` 구현 · `CommunityMcpTools`)는 MCP SDK를 모르고 app 조회 서비스만 부른다(SDK 없이 단위 테스트). SDK 타입은 전송 쪽 `CommunityMcpSdkBinding`에만 둔다. SDK 버전 · 전송 방식을 바꿀 때는 이 파일과 `in.config.CommunityMcpConfig`만, 도구 추가 · 출력 칸은 도구 정의 하나(+ 필요하면 app 조회)만 고친다
  - 응답은 집계 · ID · 상태 · 분류만 담는다. 리뷰 본문 · 사진 주소 · 닉네임 · 회원 ID · DLT 원본 본문 · 예외 메시지 · 처리한 관리자는 넣지 않고, 자유 문구가 될 수 있는 칸(토픽 · eventType · 예외 이름 · 메시지 키 · 이벤트 ID)은 정해진 형식일 때만 내보낸다(`McpSafeText`, 아니면 null)
  - 잘못된 인자 · 업무 오류는 도구 오류(isError, 정해진 문구)이고 서버 오류가 아니다. MVC 밖이라 관리자 헤더(X-Admin-Id)는 `CommunityMcpAdminFilter`가 공용 관리자 규칙과 같게 확인한다(401 ProblemDetail)
- 수신으로 처음 보는 행을 만들 때는 먼저 조회하고, 저장은 따로 연 트랜잭션(`ReviewSeparateTransaction`)에서 바로 반영한다. ID를 직접 넣는 엔티티(상품 복사본)는 Spring Data `save`가 merge가 되어 이미 커밋된 행을 덮어쓰므로, 새 행은 persist만 하는 저장소 조각(`out.store.ReplicaProductInsert`)으로 넣는다. 동시 수신으로 그 행의 유일 제약(SQLState 23505 + 제약 이름)에 막히고 다시 조회해 행이 있으면 "이미 있음"으로 수렴한다(복사본은 이어서 늦을 때만 덮어쓰기). 그 밖의 위반은 던진다(재시도 → DLT). 로그에 값을 남기지 않는다
- 따로 연 트랜잭션은 바깥 트랜잭션이 있는 채로 열리므로 수신 한 건이 DB 커넥션을 최대 2개 쓴다. Kafka 리스너는 동시성 1이라 커넥션 2개로 끝나고, Spring 이벤트 경로는 발행 쪽 스레드 수만큼 동시에 2개씩 들 수 있다(커넥션 풀 크기를 정할 때 본다)
- 저장했다가 다시 비교 · 정렬 · 커서에 쓰는 시각(`occurredAt` · `confirmedAt` · 생성 · 수정 시각)은 도메인에서 마이크로초로 잘라 저장 · 비교한다(`ReviewTimes`). 실행 환경에 따라 나노초가 나오는데 DB는 마이크로초로 저장해, 자르지 않으면 같은 이벤트가 "더 늦은" 값으로 보인다. 테스트의 기준 시각은 나노초가 있는 고정값으로 쓴다
- 목록은 커서 페이지네이션 + 인덱스, 쿼리 수 테스트로 N+1을 막는다
- 수정 API(PATCH)는 보낸 필드만 바꾼다: 키가 없으면 그대로, 키를 null(또는 틀린 타입)로 보내면 그 필드의 400(핏은 null이 지우기, 사진은 빈 목록이 모두 지우기), 바꿀 필드가 하나도 없으면 400. 요청 DTO는 필드를 JSON 원래 타입(JsonNode)으로 받아 "키 없음"과 "null"을 구분한다
- 마이그레이션은 `src/main/resources/db/migration/community/V{yyyyMMddHHmm}__community_{설명}.sql`, 추가만(이미 적용된 파일은 고치지 않는다). 커뮤니티 서비스(`apps/community-service`)가 적용하고, 커뮤니티 표가 이미 있는 DB는 baseline(`202611031200`, 고정)까지 건너뛰므로 새 파일은 그보다 뒤 버전(만든 시각)이어야 한다. 이미 칸 · 표가 있을 수 있는 DB를 생각해 `IF NOT EXISTS`로 쓴다

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
- 상태 전이 결과 타입 · `@Version` 낙관적 잠금: 리뷰 · 찜 · 복사본은 상태가 단순하고, 같은 행을 동시에 바꾸는 경쟁은 유일 제약 · 원자적 UPDATE · 상품별 잠금 안의 `occurredAt` 비교로 막는다
- DLT 복구(#741): 커뮤니티 소유 DLT(주문 · 상품 · 회원 이벤트, `CommunityDeadLetterKafkaListener` 한 리스너)를 전용 컨테이너 처리기로 읽어 `community.dead_letter`에 보관한다. DLT 토픽 · 파티션 · 오프셋당 1건이며 DB 저장 전에는 처리 완료로 넘기지 않는다. 저장 실패는 최대 60초 간격으로 끝없이 재시도하고 DLT의 DLT는 만들지 않는다. 원본 본문은 DB에만 보관하고 로그 · 관리자 응답에 넣지 않는다.
- 관리자는 `/api/v1/admin/community/dead-letters` 목록 · 상세 · `/{id}/reprocess`로 복구한다. 정상 Kafka 처리기와 같은 decode/검증/저장 경로(각 Kafka 리스너의 `handle`, 처리했으면 true)를 `CommunityDeadLetterReplayer`가 원래 토픽별로 직접 호출하고 원래 토픽에 보내지 않는다. 새 수신 토픽을 더하면 DLT 리스너 토픽 목록 · 재처리 분기 · 테스트 가드에 함께 넣는다. 미지원/깨진 메시지는 OPEN과 실패 종류를 유지하며 성공하면 REPROCESSED다. 이미 성공한 기록은 재실행하지 않는다. 동시 재처리도 자격은 주문상품당 1건이고, 늦게 끝난 실패가 성공 기록을 되돌리지 않는다.
- 재처리 중 DLT 행 잠금이나 바깥 트랜잭션을 유지하지 않는다. 실제 반영은 원래 처리기의 트랜잭션으로, 처리 결과는 별도 짧은 조건부 UPDATE로 기록한다. 반영 후 결과 기록이 실패하면 OPEN이 남고 다시 시도해도 1회분으로 수렴한다. DLT DB 행은 자동 삭제하지 않는다.
- Kafka DLT는 별도 보존 기간을 지정하지 않아 브로커 정책을 따른다(환경의 `retention.ms` 확인). 보관 소비자가 중단돼 Kafka 보존 기간을 넘긴 누락은 주문 소유 관리자 API `/api/v1/admin/orders/purchase-confirmed-events`로 구매확정 사실을 재발행해 복구한다. 기존 `order-item.purchase-confirmed` 계약 · 주문 키 · 실제 confirmedAt을 유지하고, 커뮤니티는 정상 수신 경로로 자격을 채운다. 주문 외 스키마 직접 조회나 수신 그룹 오프셋 변경은 하지 않는다.
- 아웃박스 · 릴레이 스케줄러: 커뮤니티가 이벤트를 발행하지 않는 동안은 필요 없다
- 필요해지면 이 문서를 먼저 고치고 넣는다

## 6. 테스트
- 단위 테스트는 `test`, 스프링 + Testcontainers는 태그로 나눠 `integrationTest`(`@IntegrationTest`) · `functionalTest`(`@FunctionalTest`)로 이 모듈에서 돈다. 규칙 테스트는 `archTest`
- 주석 · 컨테이너 · 정리는 모듈 공통 `boundedcontext.support`(앱의 `com.bbcc.kidly.support`와 다른 패키지)를 쓰고, 테스트 앱은 `CommunityTestApplication`(리뷰 · 찜 컨텍스트 · `common`만)이다. 테스트가 끝나면 `community` 스키마의 표를 비운다(`CommunityDatabaseCleanerExtension`)
- `@IntegrationTest` · `@FunctionalTest`를 쓰는 테스트 클래스에 개별 설정(`@MockitoBean`, `@TestPropertySource` 등)을 붙이지 않는다(컨텍스트가 새로 뜬다)
- 커뮤니티 테스트는 다른 도메인 코드를 쓰지 않는다(`CommunityTestIndependenceTest`). 다른 도메인이 보내던 이벤트는 테스트가 직접 넣는다
- 쓰기 입력마다 "두 번 → 1회분" 테스트, 카운터는 동시 호출 테스트를 둔다. 경계(아래 · 정확히 · 위)를 확인하고, 시간은 now 기준 상대값으로 쓴다
