# 커뮤니티 도메인 설계 규칙

커뮤니티 모듈(리뷰 · 찜 · 활동 기록 컨텍스트) 안의 설계 규칙이다. 도메인 경계 · `shared` 계약 · Kafka 토픽/키/그룹 · 묻고 바꿀 것은 루트 `CLAUDE.md`를 따르고, 여기와 부딪히면 루트가 우선이다.

## 1. 컨텍스트 · 계층과 포트
- 이 모듈은 컨텍스트 셋이다: `boundedcontext.review`(중심: 리뷰 · 작성 자격 · 상품 복사본 · 회원 탈퇴 수신) · `boundedcontext.favorite`(찜) · `boundedcontext.activity`(활동 기록 · 이벤트 발행, 다른 컨텍스트를 부르지 않는다). 저장은 모두 `community` 스키마다
- 컨텍스트 사이는 in · out으로만 잇는다(`api` 패키지는 두지 않는다). 받는 쪽은 들어오는 포트를 자기 `app.port.in`에 두고 app이 구현한다. 부르는 쪽은 필요한 것을 자기 `app.port`에 자기 말로 두고, `out.{상대}` 어댑터가 상대 `app.port.in`을 불러 값을 옮겨 담는다. 다른 컨텍스트를 아는 것은 이 어댑터뿐이다(app · domain · in은 모른다). 예: 찜은 상품 정보를 review의 `app.port.in.ProductCatalog`(상품 ID 묶음 → 이름 · 썸네일 · 표시 상태, 한 쿼리)로 읽고, 찜 쪽 `app.port.FavoriteProducts`를 `favorite.out.review` 어댑터가 구현한다. 시각 · 커서 · size 도우미처럼 같은 규칙의 코드도 컨텍스트마다 따로 둔다(컨텍스트 사이 참조 금지)
- `domain`: 업무 규칙 · 불변식(엔티티 · 값, JPA 엔티티 겸용). JPA 표준 어노테이션만 쓰고 Spring · `common` · 로그를 쓰지 않는다. 업무 판단은 domain 메서드가 하고 app은 순서만 조율한다. 불변식 위반은 도메인 예외다
- `app`: 유스케이스 서비스. 오류 코드는 컨텍스트마다 `ErrorCode`를 구현한 enum(`ReviewErrorCode` · `FavoriteErrorCode`)과 `BusinessException`
- `app.port`: 나가는 것(저장소 · 다른 컨텍스트)의 인터페이스, `app.port.in`: 다른 컨텍스트가 들어오는 인터페이스. 둘 다 최상위는 인터페이스만 두고, 주고받는 값은 그 인터페이스 안에 둔다(값은 record, 종류는 enum). 나가는 포트 구현은 `out`(저장소는 Spring Data `Jpa…Repository`가 포트를 상속), 들어오는 포트 구현은 app
- `in`: `web`(컨트롤러 · `dto`, 관리자 API는 `web.admin`) · `event`(이벤트 수신 · 매퍼) · `scheduler`(주기 실행, app 서비스만 부른다) · `mcp`(운영 조회 MCP 창구, app 조회 서비스만 부른다) · `config`(Kafka 리스너 팩토리 · DLT). `domain`과 `out`을 쓰지 않는다
- `out`: `persistence`(Spring Data JPA 저장소만) · `config`(설정 값) · `store`(Spring Data 저장소 조각 · 기술 보관 행/저장소 어댑터. `persistence` 저장소가 상속한다. 예: 새 행만 넣는 `ReplicaProductInsert`, 주기 실행 잠금 `SessionAdvisoryRunLock`, 활동 행 JDBC 저장 `ActivityStore`) · `storage`(DB 밖 파일 저장소 어댑터. 예: 리뷰 사진 업로드 주소를 서명하는 `S3ReviewImageStorage`) · `moderation`(리뷰 내용 검사기 어댑터: 금칙어 · OpenAI 호환 AI와 이어 붙이기) · `messaging`(메시지 보내기 어댑터: Boot 기본 KafkaTemplate으로 이벤트 보내기) · `{상대 컨텍스트}`(어댑터). 바로 아래 하위 패키지는 이 일곱만 둔다(`CommunityPackageLayoutArchitectureTest`)
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
  - 쓰이지 않는 사진 정리(#1036): 수정으로 빠진 사진 · 리뷰 삭제로 남은 사진(CASCADE 전에 주소를 읽는다)은 그 트랜잭션에서 정리 대기 표(`review_image_cleanup`, key당 한 행, ON CONFLICT DO NOTHING)에 넣고, 다시 붙이면(작성 · 수정) 같은 트랜잭션에서 대기를 지운다(`ReviewImageCleanupQueue`). 정리 스케줄러(`in.scheduler`, 서버 한 대 = 검사와 다른 이름의 세션 advisory lock)가 주기마다 대기 처리 → 훑기 한 쪽을 한다(`ReviewImageCleanupService`)
    - 지우는 key는 `{이 환경 key-prefix}/reviews/{양수 회원 ID}/{소문자 UUID}.{jpg|png|webp}` 모양만(`ReviewImageKeys`). dev · prod가 한 버킷이라 다른 환경 · 다른 기능 파일은 대기 · 훑기 어느 쪽에서도 지우지 않는다(정리용 저장소 `app.port.ReviewImageCleanupStorage`가 저장소를 부르기 전에 다시 본다)
    - 대기: 넣은 뒤 `pending-delay`(최소 5분)가 지난 행만, 지우기 직전에 그 주소가 지금 리뷰에 붙어 있으면 지우지 않고 행만 지운다. 저장소 호출은 트랜잭션 밖, 결과 반영은 한 건 = 트랜잭션 하나(읽은 시도 횟수가 그대로일 때만). 실패는 시도 +1 · 1분부터 두 배씩(상한 `max-backoff`) 미루고 시도 상한은 없다
    - 훑기: 이 환경 리뷰 경로를 ListObjectsV2로 한 쪽씩 읽어, 올라간 지 `orphan-grace`(업로드 주소 만료보다 길어야 한다, 아니면 기동 실패)가 지났고 붙어 있지도 대기 중도 아닌 파일만 지운다(한 주기 성공한 삭제 `scan-delete-limit`까지. 지우지 못한 파일은 이번 바퀴에서 건너뛰고 다음 바퀴에 다시 본다). 붙어 있는지 · 대기 중인지는 쪽마다 한 번씩 묶어 묻는다(파일 수와 상관없는 쿼리 수). 이어 읽기 표지는 서버 메모리
    - 지우기 · 목록 방식은 업로드 확인과 같은 이름(`upload-check`: s3 · none · 테스트 fake, `out.storage.ReviewImageObjectStore`)으로 고른다. none처럼 S3에 닿지 않으면 켜도 돌지 않는다. `cleanup.enabled`는 기본 false(스케줄러 `matchIfMissing = false`)이고, 삭제 · 목록 권한을 확인한 서버 환경에서만 환경변수 `COMMUNITY_REVIEW_IMAGE_CLEANUP_ENABLED`로 켠다. 로컬에서 dev · prod 프로필과 삭제 권한 있는 자격 증명으로 띄우면 그 환경 경로에서 로컬 DB에 없는 사진을 지우므로 켜지 않는다. 로컬 · 테스트 프로필은 한 번 더 false. 로그는 주기마다 건수 · 결과 · 실패 종류 한 줄이고 key · 주소를 넣지 않는다
    - 관리자 숨김 리뷰 · 탈퇴 회원 리뷰의 사진은 리뷰가 남아 있으므로 지우지 않는다
    - 공개 주소 형식 주의: "지금 리뷰에 붙어 있는지"는 `review_image.image_url`의 공개 주소 전체 문자열로 비교한다. 공개 주소 형식(호스트 · CDN)을 바꾸면 기존 행과 어긋나 그 환경의 사진을 모두 지울 수 있다. 공개 주소 형식을 바꾸기 전에 정리를 끄고, key 기준 비교로 바꾼 뒤 켠다
    - 남은 위험: 확인과 지우기 사이에 같은 사진을 다시 붙이면 지워질 수 있다. 그 틈은 대기 처리는 "붙어 있는지 확인과 지우기 사이의 S3 왕복 시간", 훑기는 "한 쪽을 묶어 확인한 뒤 그 쪽의 삭제 루프가 도는 동안"이다. 대기는 pending-delay가, 훑기는 orphan-grace가 그 틈에 다시 붙을 일을 줄이고, 다시 붙일 때의 업로드 확인(#1030)은 이미 지워진 사진이면 400으로 막는다. 틈을 없애려면 S3 호출 동안 잠금을 쥐어야 해서(외부 호출은 트랜잭션 밖 규칙과 부딪힘) 넣지 않았다
- 리뷰 내용 검사(#1002): 리뷰는 저장하면 바로 공개(`PENDING`)이고, 스케줄러(`in.scheduler`)가 주기마다 대기 리뷰를 검사해 걸리면 숨긴다(`HIDDEN`, 관리자 판단 대기). 관리자는 숨김 확정(`CONFIRMED_HIDDEN`) · 다시 공개(`RESTORED`)를 한다
  - 검사는 `app.port.ContentModerator` 하나로만 부르고 결과는 커뮤니티 말(PASSED · FLAGGED(이유 분류) · UNDECIDED(원인))이다. 제공자 요청 · 응답 해석 · 분류 대응(Llama Guard S1~S14 → 이유)은 `out.moderation` 어댑터 안에서만 한다. 검사기 조합(`moderators`) · 금칙어 · AI 주소 · 모델 · 키(환경변수) · 시간은 `community.yml` 설정이다. 새 제공자는 `ModerationAdapter` 하나를 더하고 이름을 설정에 넣는다. 모든 어댑터는 같은 계약 테스트(`ContentModeratorContractTest` 상속)를 통과한다
  - 실행: 서버 한 대만(PostgreSQL 세션 advisory lock, 못 잡으면 건너뜀), 검사기 호출은 트랜잭션 밖, 결과 반영은 한 건 = 트랜잭션 하나(`ReviewModerationRecorder`가 행 잠금으로 다시 읽어 버전 · 시도 횟수가 그대로이고 아직 대기일 때만). 판단 못 함(장애 · 시간 초과)은 대기 그대로 시도 +1, 429는 시도 횟수를 올리지 않고 그 주기의 남은 건을 멈춘다. 재시도 상한에 닿으면 대기 목록에서 빠지고 공개 그대로(관리자 목록 `attemptsExhausted`)
  - 글 · 사진이 실제로 바뀐 수정은 버전 +1이고, 공개 상태면 다시 대기(숨김 상태는 그대로). 별점 · 핏만 바뀐 수정은 그대로
  - 검사 상태 칸(`review.moderation_status`)에는 DB 기본값이 없다(#1042). 새 리뷰의 `PENDING`은 엔티티가 넣고, JPA를 거치지 않고 칸 없이 넣으면 NOT NULL 위반이다. 테스트 · 시드에서 리뷰를 직접 넣을 때는 값을 넣는다
  - 집계: 숨김 리뷰는 상품 요약 집계에 들어 있지 않다. 공개 → 숨김이면 그 리뷰 몫(수 · 별점 · 핏)을 빼고, 숨김 → 공개면 그때 값으로 더한다(같은 트랜잭션, 실제로 바뀔 때만). 숨김 리뷰의 수정 · 삭제는 집계를 건드리지 않는다. 숨김 · 복원 · 수정 · 삭제 · 탈퇴 뒤 요약 = 공개 리뷰만 다시 센 값(`ReviewModerationStatsIntegrationTest`)
  - 로그는 리뷰 ID · 결과 · 이유 · 원인만. 키 · 요청/응답 본문 · 리뷰 본문 · 사진 주소를 넣지 않는다
- 운영 조회 MCP 창구(#1005, 읽기 전용): `POST /api/v1/admin/community/mcp`, 스위치 `kidly.community.mcp.enabled`(기본 false) · 목록 상한 `max-results`. 도구는 review_summary · dead_letters · dead_letter_detail · moderation_queue · replica_health
  - 도구 정의(`in.mcp`의 `CommunityMcpTool` 구현 · `CommunityMcpTools`)는 MCP SDK를 모르고 app 조회 서비스만 부른다(SDK 없이 단위 테스트). SDK 타입은 전송 쪽 `CommunityMcpSdkBinding`에만 둔다. SDK 버전 · 전송 방식을 바꿀 때는 이 파일과 `in.config.CommunityMcpConfig`만, 도구 추가 · 출력 칸은 도구 정의 하나(+ 필요하면 app 조회)만 고친다
  - 응답은 집계 · ID · 상태 · 분류만 담는다. 리뷰 본문 · 사진 주소 · 닉네임 · 회원 ID · DLT 원본 본문 · 예외 메시지 · 처리한 관리자는 넣지 않고, 자유 문구가 될 수 있는 칸(토픽 · eventType · 예외 이름 · 메시지 키 · 이벤트 ID)은 정해진 형식일 때만 내보낸다(`McpSafeText`, 아니면 null)
  - 잘못된 인자 · 업무 오류는 도구 오류(isError, 정해진 문구)이고 서버 오류가 아니다. MVC 밖이라 관리자 헤더(X-Admin-Id)는 `CommunityMcpAdminFilter`가 공용 관리자 규칙과 같게 확인한다(401 ProblemDetail)
- 운영 지표(#1068): `review.app.CommunityMetrics` 한 곳에 이름 · 라벨 값을 둔다(라벨은 고정 집합뿐, 리뷰 · 회원 · 상품 ID · 닉네임 금지). in(리스너 · 스케줄러)과 app 서비스는 기록 메서드만 부르고, 게이지는 운영 조회(MCP)와 같은 app 조회로 수집할 때 읽는다(실패하면 NaN)
  - DLT: 미처리 수 `kidly.community.dlt.messages{state=open}` · 가장 오래된 미처리 나이 · 보관 실패 카운터(재시도마다) · 마지막 보관 시각(처음 전 NaN)
  - 검사: 대기 수 `kidly.community.moderation.pending{stage=waiting|retrying|exhausted}`(세 값은 한 번 읽은 묶음을 10초 캐시) · 상한 전 가장 오래된 대기 나이 · 검사기 호출 결과 카운터 `checks{result, cause}`(호출마다 센다, 반영 여부와 상관없다)
  - 스케줄러 마지막 성공 시각 `kidly.community.scheduler.last.success.seconds{task}`: 켜진 스케줄러만 등록하고 처음 값은 기동 시각. 끝까지 돈 주기만 갱신(잠금을 못 잡아 건너뜀 · 예외 · 사진 저장소에 닿지 않는 설정은 그대로)
  - 사진 업로드 확인 불가(503) 카운터 · 상품 복사본 마지막 반영 뒤 지난 시간(복사본이 없으면 NaN)
  - 레지스트리는 앱이 준다(community-service: actuator + Prometheus, `/actuator/prometheus`). 모듈 테스트 앱은 메모리 레지스트리(`support.CommunityTestMeterRegistryConfig`)
- 활동 기록(#1128, 통계 1차): 작성 자격 생성 · 리뷰 작성/수정/삭제 · 찜 추가/삭제 · 검사 결정을 표 `community.activity`(append-only, 인덱스 `(occurred_at, id)`만, FK · 업무 제약 없음)에 한 행씩 남긴다
  - 종류는 `activity.domain.ActivityType`, 속성 키는 `ActivityAttribute` 한 곳에만 둔다. 종류마다 속성은 `Activity`의 생성 메서드가 정한다. 회원 ID · 닉네임 · 본문 · 사진 주소는 넣지 않는다
  - review · favorite은 자기 `app.port`의 기록 포트(`ReviewActivityLog` · `FavoriteActivityLog`) → 자기 `out.activity` 어댑터 → activity `app.port.in.ActivityRecorder`로 부른다. 기록은 원래 변경과 같은 트랜잭션에만 합류한다(`Propagation.MANDATORY`, 밖에서 부르면 실패)
  - 실제로 바뀐 것만 남긴다: 이미 있는 자격 · 이미 찜 · 없는 찜 취소 · 같은 값 수정 · 버전이 바뀌어 버린 검사 결과 · 판단 못 함 · 같은 값 관리자 요청은 남기지 않는다. 회원 탈퇴로 지운 찜은 delete … returning으로 받은 행을 한 문장(INSERT … SELECT unnest)으로 남긴다(까닭 withdrawn)
  - 흐름 카운터(`activity.app.ActivityMetrics`, 라벨 고정)는 커밋 뒤에만 오른다(afterCommit): `kidly.community.reviews{action}` · `favorites{action, cause}` · `moderation.decisions{by, status}`. 작성 자격 생성 · 백필은 세지 않는다
  - 백필(마이그레이션 V202611101700): 지금 남은 작성 자격 · 리뷰 · 찜 · 통과/대기가 아닌 검사 결정을 원본 시각으로, `backfilled: true`와 `NOT EXISTS`(같은 종류 · 대상)로 다시 돌려도 중복이 없다. 이미 지워진 리뷰 · 찜 · 과거 수정 · 통과 결정은 원본이 없어 넣지 않는다
- 이벤트 발행(#1274): 토픽 `kidly.community.events`(파티션 3 · 복제 1, `activity.out.config`의 `NewTopic` 빈)에 `review.changed`(키 리뷰 ID) · `favorite.added` · `favorite.removed`(키 회원 ID, 까닭 USER · MEMBER_WITHDRAWN)를 보낸다. 이벤트 record는 `shared.community.event`
  - 활동 기록과 같은 지점 · 같은 트랜잭션에서 `ActivityRecordingService`가 활동 행과 함께 `CommunityEventOutboxWriter`를 불러 아웃박스(`community.event_outbox`)에 본문 완성본(공통 칸 7개 + data)을 넣는다. 그래서 보내지 않는 경우도 활동 기록과 같다(바뀐 것 없는 수정 · 이미 찜 · 없는 찜 해제 · 버린 검사 결과 · 판단 못 함 · 같은 값 관리자 요청 · 작성 자격 생성). 회원 ID는 찜 이벤트 본문에만 쓰고 활동 행에는 넣지 않는다
  - `visible`: 작성 · 검사 통과 · 다시 공개는 true, 삭제 · 검사 숨김 · 숨김 확정은 false, 수정은 그때 공개 여부
  - 릴레이(`activity.in.scheduler.CommunityEventRelayScheduler`, 팀 보내기 스위치 `kidly.kafka.producer.enabled`를 따른다)가 서버 한 대(세션 advisory lock `kidly.community.event-relay`)에서 주기마다 id 순으로 한 건씩 보내고(Boot 기본 KafkaTemplate, 헤더 eventType · eventId, `__TypeId__` 없음) 보낸 행은 바로 지운다. 앞 행이 아직 다시 보낼 때가 아니거나 실패하면 그 주기를 멈춰 뒤 행이 앞지르지 않는다. 실패는 1초부터 두 배(상한 60초)로 미루고, 10번이면 격리(행은 남김)하고 다음 행으로 넘어간다(그 키의 순서는 여기서 깨질 수 있어 지표 · 로그로 알린다). 설정은 `kidly.community.events.relay.*`
  - 받는 쪽 전제: 최소 한 번 전달이다. 보낸 뒤 지우기 전에 서버가 멈추면 같은 eventId가 한 번 더 갈 수 있고, 순서는 키 단위로 대체로 지키지만 커밋 순서와 아웃박스 id 순서가 다를 때 · 격리 뒤 수동으로 다시 보낼 때 바뀔 수 있다. 받는 쪽은 eventId로 중복을 거르고, 리뷰는 reviewId, 찜은 (memberId, productId)마다 occurredAt이 더 늦은 것만 반영한다(topics.md에도 같은 문구)
  - 행 지우기 · 실패 기록 · 격리는 읽은 행과 id · eventId가 모두 같을 때만 바꾼다(id를 다시 쓰는 DB에서도 다른 행을 건드리지 않게)
  - DB 연결 수: 스케줄러(검사 · 사진 정리 · 릴레이)는 한 스레드(Spring 기본 스케줄 풀 1)에서 차례로 돌아, 한 번에 잠금 연결 1 + 작업 연결 1 = 2개를 쓴다(Hikari 기본 풀 10). 스케줄러 풀을 나누면 이 계산을 다시 한다
  - 스케줄 풀: 릴레이는 Boot 기본 스케줄 풀(스레드 1)을 검사 · 사진 정리 스케줄러와 함께 쓴다. 브로커가 없으면 한 건 보내기가 Kafka 생산자 `max.block.ms`(기본 60초)까지 막혀 다른 주기가 밀릴 수 있다(전용 풀 분리는 뒤로 미룬다)
  - 격리 행 처리: 지표 `outbox.messages{state=quarantined}`가 0보다 크면 `last_failure_type`으로 원인을 확인하고 고친 뒤, 다시 보내려면 `UPDATE community.event_outbox SET quarantined_at = NULL, attempts = 0, next_attempt_at = LOCALTIMESTAMP WHERE id = :id;`를 실행한다. 그 사이 같은 키의 뒤 이벤트가 먼저 나갔을 수 있어 받는 쪽에서 순서가 뒤바뀔 수 있다(받는 쪽은 occurredAt으로 늦은 것만 반영)
  - 지표(`activity.app.CommunityEventMetrics`): `kidly.community.outbox.messages{state=pending|quarantined}` · `outbox.oldest.age.seconds` · `outbox.send.failures` · `scheduler.last.success.seconds{task=event_relay}`(켜진 릴레이만, 처음 값은 기동 시각). 로그는 eventType · eventId · 키 · 실패 종류만
- 수신으로 처음 보는 행을 만들 때는 먼저 조회하고, 저장은 따로 연 트랜잭션(`ReviewSeparateTransaction`)에서 바로 반영한다. ID를 직접 넣는 엔티티(상품 복사본)는 Spring Data `save`가 merge가 되어 이미 커밋된 행을 덮어쓰므로, 새 행은 persist만 하는 저장소 조각(`out.store.ReplicaProductInsert`)으로 넣는다. 동시 수신으로 그 행의 유일 제약(SQLState 23505 + 제약 이름)에 막히고 다시 조회해 행이 있으면 "이미 있음"으로 수렴한다(복사본은 이어서 늦을 때만 덮어쓰기). 그 밖의 위반은 던진다(재시도 → DLT). 로그에 값을 남기지 않는다
- 따로 연 트랜잭션은 바깥 트랜잭션이 있는 채로 열리므로 수신 한 건이 DB 커넥션을 최대 2개 쓴다. Kafka 리스너는 동시성 1이라 커넥션 2개로 끝나고, Spring 이벤트 경로는 발행 쪽 스레드 수만큼 동시에 2개씩 들 수 있다(커넥션 풀 크기를 정할 때 본다)
- 저장했다가 다시 비교 · 정렬 · 커서에 쓰는 시각(`occurredAt` · `confirmedAt` · 생성 · 수정 시각)은 도메인에서 마이크로초로 잘라 저장 · 비교한다(`ReviewTimes`). 실행 환경에 따라 나노초가 나오는데 DB는 마이크로초로 저장해, 자르지 않으면 같은 이벤트가 "더 늦은" 값으로 보인다. 테스트의 기준 시각은 나노초가 있는 고정값으로 쓴다
- 리뷰 수정 시각(`review.updated_at`, 응답 `updatedAt` · 화면 「수정됨」)은 작성자 수정으로 별점 · 핏 · 본문 · 사진 목록(순서 포함) 중 실제로 바뀐 것이 있을 때만 바꾼다(#1308). 그 판단은 `ReviewService`의 수정 반영에서 한 번 하고 수정 시각(`Review.markEdited`)과 활동 기록(REVIEW_UPDATED)이 함께 쓴다(같은 값 · 같은 사진 목록을 다시 보내면 둘 다 그대로). 리뷰는 `ReviewTimeEntity`의 flush 자동 갱신을 끈다(`updatesTimeOnFlush` false). 검사 반영 · 관리자 조치 · 탈퇴 표시는 바꾸지 않는다. 다른 리뷰 컨텍스트 엔티티는 자동 갱신 그대로
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
- 필요해지면 이 문서를 먼저 고치고 넣는다

## 6. 테스트
- 단위 테스트는 `test`, 스프링 + Testcontainers는 태그로 나눠 `integrationTest`(`@IntegrationTest`) · `functionalTest`(`@FunctionalTest`)로 이 모듈에서 돈다. 규칙 테스트는 `archTest`
- 주석 · 컨테이너 · 정리는 모듈 공통 `boundedcontext.support`(앱의 `com.bbcc.kidly.support`와 다른 패키지)를 쓰고, 테스트 앱은 `CommunityTestApplication`(리뷰 · 찜 · 활동 기록 컨텍스트 · `common`만)이다. 테스트가 끝나면 `community` 스키마의 표를 비운다(`CommunityDatabaseCleanerExtension`)
- `@IntegrationTest` · `@FunctionalTest`를 쓰는 테스트 클래스에 개별 설정(`@MockitoBean`, `@TestPropertySource` 등)을 붙이지 않는다(컨텍스트가 새로 뜬다)
- 커뮤니티 테스트는 다른 도메인 코드를 쓰지 않는다(`CommunityTestIndependenceTest`). 다른 도메인이 보내던 이벤트는 테스트가 직접 넣는다
- 쓰기 입력마다 "두 번 → 1회분" 테스트, 카운터는 동시 호출 테스트를 둔다. 경계(아래 · 정확히 · 위)를 확인하고, 시간은 now 기준 상대값으로 쓴다
