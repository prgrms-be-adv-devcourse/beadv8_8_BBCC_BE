# 검색 도메인 (#526)

상품 목록 검색(`GET /api/v1/products`)을 상품에서 떼어 맡는다. 상품 · 판매자가 발행한 이벤트로만 색인하고, 상품 DB를 읽지 않는다.

## 진행 상태
- 됨: 모듈, 검색 문서 계산(`domain/SearchProduct`), 상호명(`domain/SearchSeller`), 이벤트 반영(`app/SearchIndexService`), Elasticsearch 저장 · 색인 만들기(`out`), 목록 검색 쿼리(`app/port/ProductSearch` · `out/ElasticsearchProductSearch`), 목록 API(`in/ProductListController` · `app/ProductListService`), 검색 서비스 `apps/search-service` · 로컬 Elasticsearch(nori), Kafka 수신(`in/SearchProductEventListener` · `in/SearchSellerEventListener`)
- 남음: 서버 배포(추가 서버 t3.medium의 compose · 배포 워크플로 · Alloy · Kafka 사설 IP 연결), 처음 채우기(상품 · 판매자의 전체 다시 발행), nginx 전환
- kidly-app에는 넣지 않는다. 상품의 목록 API와 경로(`GET /api/v1/products`)가 같아 한 앱에 두면 매핑이 겹친다. `apps/search-service`로만 띄운다

## 검색 문서 (`domain/SearchProduct`)
- `ProductChangedEvent` 하나로 만든다. 계산 규칙은 상품의 `product_search_document`와 같다
  - 표시 가격: 판매할 수 있는 옵션(ON_SALE · 품절 아님) 중 최저가, 없으면 전체 옵션 중 최저가 (PRICE-05)
  - 품절: 모든 옵션이 품절일 때만 (SALE-07)
  - attributes: 옵션 값 · 상품 정보 값의 값 ID를 항목 code별로 모은다. 직접 입력 항목(값 ID 없음)은 뺀다
- 판매 상태로 거르지 않고 모두 저장한다. 판매중(`ON_SALE`)만 보여 주는 건 검색할 때 한다
- 상호명은 문서에 넣지 않고 `SearchSeller`로 따로 둔다. 판매자 변경 때 그 판매자의 상품 문서를 모두 고치지 않으려는 것이다

## 늦게 온 이벤트
- 저장소의 `saveIfNewer`는 저장된 값보다 `occurredAt`이 늦을 때만 덮어쓴다. 비교와 저장을 한 번에 해야 같은 상품의 이벤트가 동시에 와도 늦은 것이 남는다
  - Elasticsearch 구현은 `occurredAt`(마이크로초)을 외부 버전(`version_type=external`)으로 넘긴다. 저장된 버전보다 크지 않으면 Elasticsearch가 409로 거절하고, 저장소는 그 거절을 무시한다(`out/VersionedIndexing`)
  - 다른 오류는 그대로 던져 Kafka 수신이 다시 시도하게 한다
- 처음 채우기는 상품 · 판매자가 전체를 상품(판매자)마다 이벤트 한 개씩 다시 보낸다. 그 이벤트의 `occurredAt`은 마지막으로 바뀐 시각이라, 이미 복사본이 있는 다른 도메인은 무시하고 비어 있는 검색만 반영한다

## Kafka 수신 (`in/SearchProductEventListener` · `in/SearchSellerEventListener`)
- 구독 그룹 `kidly.search`로 `kidly.product.events`의 `product.changed`, `kidly.seller.events`의 `seller.changed`만 받는다. 그 밖의 종류(재고 차감 등)는 읽지 않고 건너뛴다. 종류는 헤더 `eventType`으로 고른다
- 본문(공통 칸 7개 + `data`)을 이벤트 record로 읽어 `SearchIndexService`에 넘긴다. DB가 없어 트랜잭션은 열지 않는다
- 같은 메시지를 다시 받거나 순서가 바뀌어 와도 외부 버전(`occurredAt`)으로 늦게 일어난 변경만 남는다. 그래서 한 번 이상 받아도 된다
- 실패 처리(`in/SearchKafkaListenerConfig`): 한 건씩 처리 완료를 커밋하고, 실패하면 1초 간격으로 3번 더 시도한 뒤 받은 토픽의 DLT(`<토픽>.search.dlt`)로 보낸다
  - 재시도 없이 바로 DLT: 읽지 못한 본문(`JacksonException`), 검색 문서로 만들 수 없는 값(옵션 없는 상품 · 필수 값 없음 · `eventType` 헤더 없음, `IllegalArgumentException` · `NullPointerException`). 다시 해도 같아서다
  - DLT 로그에는 토픽 · `eventType` · `eventId` · 키 · 예외 종류만 남긴다(본문 · 예외 메시지는 남기지 않는다)
  - Elasticsearch가 3초 넘게 응답하지 않으면 그동안 받은 메시지는 DLT로 간다. DLT를 다시 넣는 방법은 서버 배포 때 정한다
- 읽기 시작 위치는 앱 설정 `latest`(회의 10-29 안건 4). 구독 그룹이 처음 붙기 전 메시지는 읽지 않으므로 기존 상품은 처음 채우기로 넣는다
- 상품 · 판매자 토픽은 보내는 쪽이 선언한다. 검색은 DLT 두 개만 선언한다

## Elasticsearch 색인
- 색인 이름은 `{kidly.search.index.prefix}-products` · `-sellers`. dev와 prod가 Elasticsearch 하나를 색인 이름으로만 나눠 쓸 수 있다(`search.yml`)
- 필드 타입은 `src/main/resources/elasticsearch/*.json`. `dynamic: strict`라 문서에 없는 필드가 오면 저장이 거절된다. 상품 이름은 한글 형태소 분석기 `nori`(Elasticsearch에 analysis-nori 플러그인 필요)
- 기동할 때 색인이 없으면 만들고(`out/SearchIndexInitializer`), 있으면 바꾸지 않는다. 필드를 바꾸면 새 색인을 만들어 다시 채운다
- 항목 값은 `code:값ID` 한 단어로 묶어 keyword 필드 `attributeValues` 하나에 담는다. 항목이 늘어도 필드가 늘지 않는다

## 목록 검색 (`app/port/ProductSearch`, `out/ElasticsearchProductSearch`)
- 계약(조건 · 결과 · 정렬 · 페이지)은 상품의 `ProductSearch`(PostgreSQL)와 같다. 목록 API를 옮겨도 응답이 바뀌지 않게 하려는 것이다
- 판매중(`status = ON_SALE`)만 돌려준다. 조건은 모두 `bool.filter`로 건다. 정렬이 점수가 아니라 정해진 필드라 점수를 계산하지 않는다
  - 카테고리: `categoryId` 일치
  - 검색어: 상품명을 `match`(nori, `operator: and`)로 찾는다. 검색어의 모든 형태소가 상품명에 있어야 맞는다. 띄어쓰기 · 조사 · 어순이 달라도 찾고(`원피스 여아용` → `여아 원피스`), 형태소 하나만 맞는 상품까지 넓히지 않는다. 앞뒤 공백은 빼고, 공백뿐이면 거르지 않는다
  - 항목 값: 항목마다 `terms`(`attributeValues`, `code:값ID`) 하나. 같은 항목 안의 값은 하나만 맞으면 되고, 항목끼리는 모두 맞아야 한다. 값이 빈 항목은 쓰지 않는다
- 정렬: `NEWEST` = `productCreatedAt` 내림차순, `PRICE_ASC` · `PRICE_DESC` = `minPrice`. 값이 같으면 `productId` 내림차순. 관련도순은 아직 없다
- 상호명: 결과 페이지의 판매자 ID로 판매자 색인을 `mget` 한 번 해서 붙인다(같은 판매자는 한 번). 판매자 문서가 없으면 null. 결과가 없으면 찾지 않는다
- 전체 개수는 정확히 센다(`track_total_hits: true`). Elasticsearch는 `from + size`가 1만 건(`max_result_window`)을 넘으면 거절하므로, 1만 건 너머 페이지는 빈 목록과 전체 개수를, 걸친 페이지는 1만 건까지만 돌려준다
- Elasticsearch에 연결하지 못했거나 오류로 답하면(색인 없음 · 과부하 등) 상품 · 판매자 조회 모두 `app.port.SearchUnavailableException`으로 바꿔 던진다

## 목록 API (`in/ProductListController`, `app/ProductListService`)
- `GET /api/v1/products`(`@PublicApi`). 경로 · 요청 · 응답 · 검증 메시지는 상품의 목록 API와 같다. FE는 바꾸지 않고 nginx가 이 경로를 search-service로 보낸다
  - 요청 `app/dto/ProductListRequest`: `categoryId`, `keyword`(50자 이하), `attributes`(`항목code:값ID`, 20개까지), `sort`(`NEWEST` · `PRICE_ASC` · `PRICE_DESC`, 기본 `NEWEST`), `page`(0 이상, 기본 0), `size`(1~100, 기본 20)
  - 응답 `PageResponse<ProductListItemResponse>`: `productId`(문자열) · `name` · `thumbnailUrl` · `businessName` · `minPrice` · `soldOut` · `sizeFree`. 없는 썸네일 · 상호명은 null
- 검색 색인을 쓸 수 없으면(`SearchUnavailableException`) 503 `SEARCH_UNAVAILABLE` ProblemDetail로 응답하고 WARN을 남긴다(검색어는 남기지 않는다)
  - 상품의 PostgreSQL 검색으로 대신 찾지 않는다. 검색은 상품 DB를 읽지 않기 때문이다. 전환 기간에는 nginx 라우팅을 되돌려 상품의 목록 API로 돌아갈 수 있다
- 관련도순 정렬은 아직 없다(상품 목록 API에도 없다)
- 도메인 모듈의 컨트롤러 단위 테스트는 스프링을 띄우지 않고 `MockMvcBuilders.standaloneSetup` + `GlobalExceptionHandler`로 한다. HTTP 전체는 `apps/search-service`의 기능 테스트(`ProductListApiTest`)가 본다

## 검색 서비스 (`apps/search-service`)
- 검색 도메인만 띄우는 Spring Boot 앱. 상품 목록 API와 경로가 같아 kidly-app과 따로 띄운다. 서버는 추가 서버(t3.medium)에 Elasticsearch와 함께 둔다
- DB를 쓰지 않는다. `SearchServiceApplication`이 DataSource · JPA 자동 설정과 common의 JPA 설정(`JpaAuditingConfig` · `QuerydslConfig`)을 뺀다. common은 고치지 않는다
- JWT는 공개키로 검증만 한다. common의 검증기가 요구하는 `AccessTokenRevocations`(회원 DB)는 무효화를 확인하지 않는 구현으로 채운다(`SearchServiceSecurityConfig`). 이 서비스에는 로그인이 필요한 API가 없어서다. 그런 API를 더하면 이 빈부터 다시 정한다
- 실행: `docker compose up -d elasticsearch` 뒤 `./gradlew :apps:search-service:bootRun` (포트 8082, 프로필 local, 색인 `local-*`). 처음에는 nori 이미지를 빌드한다
  - 로컬 compose의 `elasticsearch`는 프로필 `search`라 기본 `docker compose up -d`에서는 뜨지 않는다. 이름을 붙여 띄우거나 `--profile search`를 쓴다
- Elasticsearch 이미지: `docker/elasticsearch/Dockerfile`(공식 9.4.x + analysis-nori). 로컬 compose · 테스트 · 서버가 같은 파일로 만든다. 버전은 클라이언트(Boot BOM)와 같은 마이너로 맞춘다
- 메모리(로컬 측정): Elasticsearch 힙 512MB → 약 950MB, search-service `-Xmx384m` → 약 290MB
- 테스트: `@IntegrationTest` · `@FunctionalTest`(이 서비스의 `support`)가 같은 Dockerfile로 Elasticsearch 컨테이너를 띄운다. Kafka 수신은 `@KafkaIntegrationTest`(Kafka 컨테이너를 더함, 리스너는 테스트 안에서 켜고 끈다)가 본다. 테스트마다 색인을 지우고 다시 만든다(`SearchIndexCleanerExtension`)
  - 문서만 지우면(delete_by_query) 안 된다. Elasticsearch가 지운 문서의 외부 버전을 잠시(`gc_deletes`, 60초) 기억해, 같은 발생 시각으로 다시 색인하면 늦은 이벤트로 보고 거절한다
  - 저장한 문서는 새로 고침 전에는 검색되지 않으므로 검색 전에 `SearchIndexes.refresh()`를 부른다
