# 개발 환경

> **여기부터는 개발 가이드입니다.** 1~4장은 기획 · 정책, 5~10장은 개발 · 운영에 필요한 것입니다. 아직 정하지 않은 것과 그동안 쓰는 값은 **2.1**에 모았습니다.

## 5.1 기술 스택

| 영역 | 선택 |
|---|---|
| 백엔드 | Java 25 · Spring Boot 4.1 · Spring Data JPA · QueryDSL · Spring Batch(정산 · 추천, 메타데이터는 각 서비스 스키마 settlement · recommend) · Spring for Apache Kafka · springdoc(Swagger) · ArchUnit(경계 · 계층 검사). 서비스는 Spring Security를 쓰지 않음(인증은 Gateway) |
| 데이터 | PostgreSQL 18 · Flyway로 스키마 관리 (`V{yyyyMMddHHmm}__*.sql`, JPA는 검증만, 적용된 파일은 고치지 않고 새 파일 추가) · 모듈마다 DB 스키마를 나눔 (member · childprofile · seller · product · recommend · orders · payment · settlement · community · notification · setting) · 암호화는 토스 paymentKey (AES-256-GCM, 대상은 2.1) · 검색은 PostgreSQL이 아니라 Elasticsearch(상품의 검색용 표는 되돌리기용으로 남음) |
| 이벤트 | Kafka. 보내는 도메인마다 토픽 하나(`kidly.{도메인}.events`, 파티션 3 · 복제 1), 키는 correlationId, 받는 도메인마다 구독 그룹 `kidly.{도메인}`과 DLT. 결제는 아웃박스 표(`payment.payment_outbox`)로 보냄. 도메인마다 Kafka 수신 스위치로 Spring 이벤트로 되돌릴 수 있음 |
| 인증 | 카카오 로그인(화면이 인가 코드를 받아 서버로 보냄) · JWT(RS256) · API Gateway(apps/gateway, WebFlux)가 검증 · 무효화 확인 · 역할 검사 · CORS |
| 외부 API | 토스페이먼츠 테스트 API(결제) · Delivery Tracker(택배 조회) · 공공데이터포털 한국천문연구원 특일 정보(정산 공휴일) · OpenAI(추천) · 카카오(로그인) · Gmail SMTP(알림) |
| 인프라 | AWS EC2 2대(t3.large: Docker Compose로 Gateway · 앱 · 검색 서비스 · PostgreSQL · Kafka / t3.medium: k3s로 Elasticsearch · 모니터링) · S3(상품 이미지) · Nginx · GitHub Actions |
| 검색 | Elasticsearch 9.4 + analysis-nori(한글 형태소), elasticsearch-java 클라이언트(Boot starter). 검색 서비스(search-service)는 DB 없이 뜨고 색인 이름 앞에 환경 이름(`dev-products`)을 붙여 dev · prod가 Elasticsearch 하나를 나눠 씀. Elasticsearch는 t3.medium k3s(heap 1g), 검색 서비스는 앱 서버 t3.large의 docker compose(heap 512m, 프로필 `search`) |
| AI 호출 | Spring AI 2.0.x(`spring-ai-starter-model-openai`), OpenAI Responses API. 구조화 출력은 `ChatClient.entity()`로 Java record에 받음. tool은 쓰지 않음 |
| chat 모델 | OpenAI 중간 등급(terra 계열). 모델 이름은 설정값. `reasoning-effort`는 low |
| 임베딩 | OpenAI `text-embedding-3-small`(1,536차원). 상품 복사본이 바뀔 때 이름 · 브랜드 · 설명 · 정보 값을 합쳐 한 번 호출. 처음 채우기는 묶음 호출. 검색어는 홈 맛보기 계산 때 묶어 호출 |
| 벡터 저장소 | P17에서 선택. Spring AI `VectorStore` 뒤에 두어 starter 교체로 바꿀 수 있음. 상품 ID · 벡터 · 거르기 칸(상태 · 카테고리 · 사이즈 무관)만 두고 본문은 복사본 표에서 읽음 |
| RAG 자료 | 성장도표 설명문, 사이즈 측정 안내(글 조각 + 출처 id). Spring AI `QuestionAnswerAdvisor`로 프롬프트에 붙임. 숫자 표(성장도표 · LMS)는 RAG가 아니라 규칙 계산 입력 |
| 추천 저장소 | 추천 DB(지금은 공용 PostgreSQL `recommend` 스키마, 분리 뒤 추천 전용). 복사본, 활동 기록, 자녀 선호 점수, AI 응답(입력 해시 키), 호출 횟수, Spring Batch 실행 기록 |
| 실패 대비 | Gateway timeout 3초 · circuit breaker, 추천 서비스 내부 timeout 2.5초, 하루 호출 상한. 실패 시 규칙 결과 또는 빈 목록 |
| 배포 | EC2 2대(t3.large: 앱 · PostgreSQL · Kafka, t3.medium: 모니터링 · ES, k3s). 추천 서비스를 k3s로 옮기는 시점은 배포 계획에 따름 |
| 모니터링 | Prometheus · Loki · Alloy · Grafana, Slack 알림 (자세한 구성은 10장) |
| 프론트 | React · Vite · Tailwind CSS · shadcn/ui · React Query · Zustand |

## 5.2 저장소

- BE: https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE
- FE: https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_FE

## 5.3 데이터 모델 변경 (최종)

세미 이후 새로 생기거나 바뀐 표만 적습니다. 정산 표는 ⑦ 상세 5에 있습니다. 모든 표는 자기 도메인 스키마에 두고, 다른 도메인의 ID는 값으로만 들고 있습니다.

| 표 · 칸 | 내용 | 비고 |
|---|---|---|
| `payment_outbox` (새 표) | 보낼 이벤트: event_id(UUID, 유니크) · event_type · topic · message_key · payload(본문 JSON) · attempt_count · last_error · sent_at(null이면 안 보냄) · created_at · updated_at. 다른 표와 FK 없음(키에 주문 · 정산서 ID) | V202610300905__payment_outbox |
| `wallet.frozen_at` (새 칸) | 탈퇴로 동결한 시각. null이면 동결되지 않은 지갑 | V202611021530__wallet_frozen_at |
| 검색 색인 (DB 아님) | products: productId · name(nori) · thumbnailUrl · sellerId · categoryId · status · sizeFree · productCreatedAt · minPrice · soldOut · attributeValues(`code:값ID`) · occurredAt / sellers: 상호명 · 상태. 모르는 칸은 거절(`dynamic: strict`) | - |
| 아웃박스 표 (주문 · 상품 · 정산 · 자녀 프로필) | 도메인마다 보낼 이벤트를 업무와 같은 트랜잭션에 저장하고 릴레이가 Kafka로 보냄 (결제와 같은 방식) | 각 도메인 Flyway |
| community `replica_product` | product_id(PK) · name · thumbnail_url · display_status · source_occurred_at. 리뷰 · 찜 화면용 상품 복사본 | 상품당 1행 |
| community `review_eligibility` | order_item_id · order_id · member_id · product_id · option_id · size · confirmed_at. 리뷰 작성 자격 | order_item_id 유일 (K30) |
| community `review` · `review_stats` | 리뷰: order_item_id · member_id · product_id · rating · content · nickname · fit · author_withdrawn_at · 검사 칸(moderation_status · moderation_reason · moderation_attempts · moderated_at · moderated_by_admin_id · content_version) · 작성 · 수정 시각 / 집계: product_id · review_count · rating_sum · fit_small · fit_true_to_size · fit_large | order_item_id 유일, 별점 1~5 CHECK, 검사 상태 5값 CHECK(DB 기본값 없음) |
| community `review_image` | review_id · position · image_url · created_at. 리뷰 사진(순서대로) | (review_id, position) 유일, 리뷰를 지우면 함께 지움(ON DELETE CASCADE) |
| community `review_image_cleanup` | object_key · reason(REPLACED · REVIEW_DELETED) · attempts · next_attempt_at · last_failure_type · created_at. 쓰이지 않는 사진 정리 대기 | object_key 유일 |
| community `dead_letter` | DLT 토픽 · 파티션 · 오프셋 · 원래 토픽 · 키 · eventType · eventId · 본문 · 실패 종류 · 상태(OPEN · REPROCESSED) · 재처리 횟수. 받지 못한 이벤트 보관(#741) | (dlt_topic, dlt_partition, dlt_offset) 유일 |
| community 찜 · 찜 집계 | member_id · product_id · 찜한 시각 / product_id · favorite_count | (member_id, product_id) 유일 |
