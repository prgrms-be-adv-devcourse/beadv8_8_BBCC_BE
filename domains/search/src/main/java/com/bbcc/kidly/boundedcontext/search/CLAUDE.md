# 검색 도메인 (#526)

상품 목록 검색(`GET /api/v1/products`)을 상품에서 떼어 맡는다. 상품 · 판매자가 발행한 이벤트로만 색인하고, 상품 DB를 읽지 않는다.

## 진행 상태
- 됨: 모듈, 검색 문서 계산(`domain/SearchProduct`), 상호명(`domain/SearchSeller`), 이벤트 반영(`app/SearchIndexService`), Elasticsearch 저장 · 색인 만들기(`out`), 목록 검색 쿼리(`app/port/ProductSearch` · `out/ElasticsearchProductSearch`), 목록 API(`in/ProductListController` · `app/ProductListService`), 검색 서비스 `apps/search-service` · 로컬 Elasticsearch(nori), Kafka 수신(`in/SearchProductEventListener` · `in/SearchSellerEventListener`), 검색 이벤트 발행(`search.performed`, #611), 의미 검색(관련도순, #797)
- 됨: dev 서버 배포(아래 "서버 배포"), dev 전환(Gateway가 `GET /api/v1/products`만 search-service로 넘긴다, `apps/gateway`), dev 처음 채우기, prod 설정(`deploy/.env.prod`, main 첫 배포 때 나간다)
- 남음: prod 처음 채우기(main 배포 뒤 판매자 → 상품 다시 발행 API를 한 번씩, `deploy/search/README.md`)
- `apps/search-service`로 띄운다. 상품 목록 API 경로는 `GET /api/v1/products`다

## 서버 배포 (`deploy/`)
- 별도 서버 없이 기존 EC2의 같은 compose 프로젝트(`-p dev`)에 컨테이너 `elasticsearch` · `search-service`를 더한다. Kafka는 같은 네트워크의 `kafka:9092`를 쓴다
- compose profile `search`라 `.env`에 `COMPOSE_PROFILES=search`가 있는 환경에서만 뜬다. dev(`deploy/.env.dev`) · prod(`deploy/.env.prod`)다. 환경마다 `SEARCH_PORT`(dev 8082 · prod 8094) · `SEARCH_INDEX_PREFIX` · `KIDLY_SEARCH_URI`를 둔다
- 이미지: 검색 서비스 `<저장소>-search-service:<커밋 SHA>`, Elasticsearch `<저장소>-elasticsearch:<docker/elasticsearch 트리 해시>`(폴더가 바뀔 때만 빌드). `deploy.yml`이 올린다
- Elasticsearch는 heap 1g, 포트를 열지 않고 보안(xpack)을 끈다. 검색 서비스는 heap 512m, 서버 `127.0.0.1:<SEARCH_PORT>`에만 연다
- 검색 서비스의 `/actuator/health`가 Elasticsearch 연결을 보므로 Elasticsearch가 healthy가 된 뒤에 띄운다
- 모니터링: Prometheus job `kidly-search`(앱 job `kidly`의 대시보드 · 알림과 나눈다), Loki `service_name=kidly-search`

## 검색 문서 (`domain/SearchProduct`)
- `ProductChangedEvent` 하나로 만든다. 계산 규칙은 상품의 `product_search_document`와 같다
  - 표시 가격: 판매할 수 있는 옵션(ON_SALE · 품절 아님) 중 최저가, 없으면 전체 옵션 중 최저가 (PRICE-05)
  - 품절: 모든 옵션이 품절일 때만 (SALE-07)
  - attributes: 옵션 값 · 상품 정보 값의 값 ID를 항목 code별로 모은다. 직접 입력 항목(값 ID 없음)은 뺀다
- 브랜드(`brand`)는 받은 값 그대로 옮긴다(#610). 등록하지 않았거나 브랜드 칸이 없는 예전 메시지면 null이다. 설명(`description`)은 의미 검색 문장에만 쓰고 색인에는 저장하지 않는다. 사이즈별 실측은 쓰지 않는다
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
- 필드 타입은 `src/main/resources/elasticsearch/*.json`. `dynamic: strict`라 설정에 없는 필드가 오면 저장이 거절된다. 상품 이름은 한글 형태소 분석기 `nori`(Elasticsearch에 analysis-nori 플러그인 필요)
  - 브랜드는 `standard`(단어 그대로, 소문자로)다. 고유명사라 nori가 앞뒤 글자에 따라 다르게 나눈다(`키들리` → `키` · `들리`, `키들리 원피스` → `키` · `리` · `원피스`). 그래서 색인한 형태소와 검색어 형태소가 어긋나 찾지 못한다
- 기동할 때 색인이 없으면 만들고, 있으면 설정 파일의 필드를 보내 새로 더한 필드만 넣는다(`out/SearchIndexInitializer`, put mapping)
  - 필드를 더하는 것은 색인을 다시 만들지 않아도 된다. 새 필드는 그 뒤에 색인한 문서부터 채워지므로 이미 있는 문서는 상품 · 판매자의 전체 다시 발행으로 채운다
  - 있는 필드의 타입 · 분석기를 바꾸면 Elasticsearch가 거절해 기동에 실패한다. 그때는 새 색인을 만들어 다시 채운다
- 의미 검색 벡터 `embedding`(`dense_vector` 512차원 · cosine · `int8_hnsw`)과 그 문장 해시 `embeddingTextHash`(색인 안 함). `index_options`를 정해 둔다(9.4 기본값 `bbq_disk`는 라이선스를 탄다)
  - Elasticsearch 9.2부터 `_source` 응답에서 벡터가 빠진다. 저장된 벡터는 `findEmbedding`이 `_source_exclude_vectors=false`로 읽는다. 목록 검색 결과에는 벡터가 오지 않는다
  - 차원 수(`kidly.search.embedding.dimensions`)와 `dims`는 같아야 한다. 바꾸면 기존 필드 타입이 달라져 기동에 실패하므로 새 색인을 만들어 다시 채운다
- 항목 값은 `code:값ID` 한 단어로 묶어 keyword 필드 `attributeValues` 하나에 담는다. 항목이 늘어도 필드가 늘지 않는다

## 목록 검색 (`app/port/ProductSearch`, `out/ElasticsearchProductSearch`)
- 계약(조건 · 결과 · 정렬 · 페이지)은 상품의 `ProductSearch`(PostgreSQL)와 같다. 목록 API를 옮겨도 응답이 바뀌지 않게 하려는 것이다
- 판매중(`status = ON_SALE`)만 돌려준다. 조건은 모두 `bool.filter`로 건다. 정렬이 점수가 아니라 정해진 필드라 점수를 계산하지 않는다
  - 카테고리: `categoryId` 일치
  - 검색어: 상품명이나 브랜드에서 찾는다(#610). 아래 둘 중 하나만 맞으면 된다. 앞뒤 공백은 빼고, 공백뿐이면 거르지 않는다
    - 상품명: 검색어 전체를 `match`(nori, `operator: and`)로 찾는다. 모든 형태소가 상품명에 있어야 맞는다. 띄어쓰기 · 조사 · 어순이 달라도 찾고(`원피스 여아용` → `여아 원피스`), 형태소 하나만 맞는 상품까지 넓히지 않는다
    - 단어별: 공백으로 나눈 단어마다 상품명(nori)이나 브랜드(`standard`) 중 한 곳에 있어야 맞는다(`키들리 원피스` → 브랜드 키들리 · 상품명 원피스). 브랜드는 단어를 다 써야 찾는다(`꼬마`로 `꼬마숲`은 못 찾음)
    - filter라 점수를 계산하지 않는다. 그래서 상품명 > 브랜드 같은 가중치는 두지 않았다(관련도순도 고정 점수라 가중치가 없다)
  - 항목 값: 항목마다 `terms`(`attributeValues`, `code:값ID`) 하나. 같은 항목 안의 값은 하나만 맞으면 되고, 항목끼리는 모두 맞아야 한다. 값이 빈 항목은 쓰지 않는다
- 정렬: `NEWEST` = `productCreatedAt` 내림차순, `PRICE_ASC` · `PRICE_DESC` = `minPrice`. 값이 같으면 `productId` 내림차순. `RELEVANCE`는 아래 "의미 검색"(검색어가 없으면 `NEWEST`와 같다)
- 상호명: 결과 페이지의 판매자 ID로 판매자 색인을 `mget` 한 번 해서 붙인다(같은 판매자는 한 번). 판매자 문서가 없으면 null. 결과가 없으면 찾지 않는다
- 전체 개수는 정확히 센다(`track_total_hits: true`). Elasticsearch는 `from + size`가 1만 건(`max_result_window`)을 넘으면 거절하므로, 1만 건 너머 페이지는 빈 목록과 전체 개수를, 걸친 페이지는 1만 건까지만 돌려준다
- Elasticsearch 쪽 장애만 상품 · 판매자 조회 모두 `app.port.SearchUnavailableException`으로 바꿔 던진다: 연결 실패, 5xx, 과부하(429), 색인 없음(404 `index_not_found_exception`). 다시 하면 될 수 있어 503으로 알린다
  - 그 밖의 4xx(400 등)는 요청을 잘못 만든 우리 쪽 버그라 `ElasticsearchException` 그대로 던진다(500, 스택 트레이스와 함께 ERROR). 503으로 가리면 원인을 찾기 어렵다

## 목록 API (`in/ProductListController`, `app/ProductListService`)
- `GET /api/v1/products`(로그인 없이 호출). 경로 · 요청 · 응답 · 검증 메시지는 상품의 목록 API와 같다. FE는 바꾸지 않고 Gateway가 이 경로를 search-service로 보낸다(`apps/gateway` `application.yml`의 `search-product-list` 라우트)
  - 요청 `app/dto/ProductListRequest`: `categoryId`, `keyword`(50자 이하), `attributes`(`항목code:값ID`, 20개까지), `sort`(`NEWEST` · `PRICE_ASC` · `PRICE_DESC` · `RELEVANCE`, 기본 `NEWEST`), `page`(0 이상, 기본 0), `size`(1~100, 기본 20)
  - 응답 `PageResponse<ProductListItemResponse>`: `productId`(문자열) · `name` · `thumbnailUrl` · `businessName` · `minPrice` · `soldOut` · `sizeFree`. 없는 썸네일 · 상호명은 null
- 검색 색인을 쓸 수 없으면(`SearchUnavailableException`) 503 `SEARCH_UNAVAILABLE` ProblemDetail로 응답하고 WARN을 남긴다. 로그에는 색인 · 상태와 원인 예외 종류만 남긴다. 원인 메시지에는 Elasticsearch가 돌려준 요청 일부(검색어)가 들어갈 수 있어 남기지 않는다
  - 상품의 PostgreSQL 검색으로 대신 찾지 않는다. 검색은 상품 DB를 읽지 않기 때문이다. 검색 장애 시 검색 서비스를 복구한다
- 관련도순(`RELEVANCE`)은 검색에만 있다
- 공개 API지만 로그인했으면 Gateway가 붙인 회원 ID 헤더(`MemberHeaders.MEMBER_ID`)를 읽어 검색 이벤트에 쓴다. `@LoginMember`는 헤더가 없으면 401이라 쓰지 않는다. 헤더가 없거나 잘못되면 비로그인으로 찾는다
- 도메인 모듈의 컨트롤러 단위 테스트는 스프링을 띄우지 않고 `MockMvcBuilders.standaloneSetup` + `GlobalExceptionHandler`로 한다. HTTP 전체는 `apps/search-service`의 기능 테스트(`ProductListApiTest`)가 본다

## 의미 검색 (#797)
- 상품명에 없는 말로도 찾게 한다(`여름 물놀이 옷` → 래시가드). 관련도순(`RELEVANCE`)일 때만 쓴다. 다른 정렬은 지금과 같다
- 임베딩: OpenAI `text-embedding-3-small`(512차원)을 `RestClient`로 부른다(`out/OpenAiTextEmbedder`, 연결 1초 · 응답 2초). 설정 `kidly.search.embedding.*`(`search.yml`), 키는 `OPENAI_API_KEY`
  - 키가 없으면(로컬 · 테스트 · CI) 끈다. 끄거나 API가 실패 · 시간 초과면 `TextEmbedder`가 비워서 돌려주고, 부르는 쪽은 벡터 없이 이어 간다(색인은 벡터 없이 저장, 검색은 키워드로만)
  - 문장(상품 정보 · 검색어)과 응답 본문은 로그에 남기지 않는다. 상태 코드 · 예외 종류만 남긴다. 상품 정보와 검색어가 OpenAI로 나간다
  - 연속 실패 차단(`out/EmbeddingCircuitBreaker`): 연속 5번 실패(연결 실패 · 시간 초과 · 오류 응답 · 쓸 수 없는 응답(빈 벡터 · 차원 불일치))하면 30초 동안 부르지 않고 바로 비워서 돌려준다. 장애 동안 검색 요청마다 시간 초과(최대 약 3초)를 기다리지 않게 하려는 것이다. 30초 뒤 호출 하나만 시험으로 보내 성공하면 닫고, 실패하면 다시 30초. 열 때 WARN · 닫을 때 INFO 한 번씩만 남긴다. 색인 · 검색이 같은 차단을 쓴다
  - 검색어 기억(`out/KeywordVectorCache`): 같은 검색어의 벡터를 10분 동안 기억해 다시 부르지 않는다(1,000개까지, 오래 안 쓴 것부터 버림 · 약 2MB). 벡터를 얻었을 때만 기억한다. 인스턴스마다 따로 둔다
  - 두 기준값은 상수로 둔다(설정으로 빼지 않음). 매번 다른 검색어로 부르는 호출량은 막지 못한다. 요청 수 제한은 Gateway 몫으로 따로 다룬다
- 색인(`app/SearchIndexService`): 상품명 · 브랜드 · 설명을 줄로 이은 문장(2,000자까지, `SearchProduct.embeddingText`)을 벡터로 바꿔 문서에 붙인다
  - 저장된 문서의 문장 해시가 같으면(재고 · 가격만 바뀐 이벤트) API를 부르지 않고 저장된 벡터를 쓴다. 이벤트마다 한 번 Elasticsearch 조회(`findEmbedding`)가 더 든다
  - API를 쓸 수 없어 벡터 없이 저장된 상품은 다음 변경 이벤트(또는 다시 발행) 때 다시 만든다
- 검색(`app/ProductListService` → `out/ElasticsearchProductSearch`): 관련도순 + 검색어면 검색어를 벡터로 바꿔 조건(`keywordVector`)에 넘긴다
  - 검색어가 상품명 · 브랜드에 있는 상품은 고정 점수 2(`constant_score`)를 받는다. 검색어 벡터가 있으면 같은 조건(판매중 · 카테고리 · 항목 값) 안에서 kNN(`k` 100 · 후보 200 · 코사인 유사도 0.3 이상)으로 뜻이 가까운 상품을 더한다. kNN 점수(0.5~1)를 더해 점수 내림차순, 같으면 `productId` 내림차순
  - 그래서 검색어가 있는 상품이 늘 먼저 오고, 그 안과 뒤에서는 뜻이 가까운 순이다. BM25 점수를 쓰지 않는 건 상한이 없어 상품 수에 따라 kNN 점수와 앞뒤가 바뀌기 때문이다
  - 의미 검색으로 더하는 상품은 100개까지다. 그보다 뒤 페이지는 검색어가 있는 상품만 나온다
- 처음 채우기: 판매자 → 상품 다시 발행 API를 한 번씩 부르면 벡터도 채워진다(상품 수만큼 API를 부른다)

## 검색 이벤트 발행 (`search.performed`, #611)
- 추천이 회원의 관심사로 쓴다. 토픽 `kidly.search.events`(파티션 3 · 복제 1, `out/SearchKafkaConfig`가 선언), 키는 회원 ID, 본문은 공통 칸 7개 + `data`
- 보내는 때: 로그인 회원이 검색어(앞뒤 공백을 빼고 비지 않은 것)로 찾은 첫 페이지. 비로그인 · 검색어 없는 둘러보기 · 다음 페이지 · 검색 실패(503)는 보내지 않는다
- `data`: `memberId` · `keyword`(앞뒤 공백 뺀 값) · `categoryId` · `attributeValueIds`(항목 code → 값 ID 목록) · `resultCount`(전체 결과 수, 0건도). 검색 시각은 `occurredAt`. 결과 목록은 담지 않는다
- DB가 없어 아웃박스를 쓰지 않는다. 검색 응답 스레드는 전송을 검색 전용 스레드 하나(대기열 1만 건)에 넘기고 기다리지 않는다. 보내지 못하면 ERROR 로그(eventType · eventId · 키 · 예외 종류)만 남기고 버린다. 검색어가 들어 있어 본문 · 예외 메시지는 남기지 않는다
- `kidly.kafka.producer.enabled=false`(Kafka 없는 테스트)면 보내지 않는 구현을 쓴다. `@KafkaIntegrationTest`만 켠다

## 검색 서비스 (`apps/search-service`)
- 검색 도메인만 띄우는 Spring Boot 앱
- DB를 쓰지 않는다. `SearchServiceApplication`이 DataSource · JPA 자동 설정과 common의 JPA 설정(`JpaAuditingConfig` · `QuerydslConfig`)을 뺀다. common은 고치지 않는다
- 토큰은 Gateway가 검증한다. 이 서비스는 토큰을 보지 않고, 로그인이 필요한 API가 없다(회원 ID 헤더는 검색 이벤트에만 쓴다)
- 실행: `docker compose up -d elasticsearch` 뒤 `./gradlew :apps:search-service:bootRun` (포트 8082, 프로필 local, 색인 `local-*`). 처음에는 nori 이미지를 빌드한다
  - 로컬 compose의 `elasticsearch`는 프로필 `search`라 기본 `docker compose up -d`에서는 뜨지 않는다. 이름을 붙여 띄우거나 `--profile search`를 쓴다
- Elasticsearch 이미지: `docker/elasticsearch/Dockerfile`(공식 9.4.x + analysis-nori). 로컬 compose · 테스트 · 서버가 같은 파일로 만든다. 버전은 클라이언트(Boot BOM)와 같은 마이너로 맞춘다
- 메모리(로컬 측정): Elasticsearch 힙 512MB → 약 950MB, search-service `-Xmx384m` → 약 290MB
- 테스트: `@IntegrationTest` · `@FunctionalTest`(이 서비스의 `support`)가 같은 Dockerfile로 Elasticsearch 컨테이너를 띄운다. Kafka 수신은 `@KafkaIntegrationTest`(Kafka 컨테이너를 더함, 리스너는 테스트 안에서 켜고 끈다)가 본다. 테스트마다 색인을 지우고 다시 만든다(`SearchIndexCleanerExtension`)
  - 문서만 지우면(delete_by_query) 안 된다. Elasticsearch가 지운 문서의 외부 버전을 잠시(`gc_deletes`, 60초) 기억해, 같은 발생 시각으로 다시 색인하면 늦은 이벤트로 보고 거절한다
  - 저장한 문서는 새로 고침 전에는 검색되지 않으므로 검색 전에 `SearchIndexes.refresh()`를 부른다
