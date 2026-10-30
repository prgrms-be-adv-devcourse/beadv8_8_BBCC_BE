# 검색 도메인 (#526)

상품 목록 검색(`GET /api/v1/products`)을 상품에서 떼어 맡는다. 상품 · 판매자가 발행한 이벤트로만 색인하고, 상품 DB를 읽지 않는다.

## 진행 상태
- 됨: 모듈, 검색 문서 계산(`domain/SearchProduct`), 상호명(`domain/SearchSeller`), 이벤트 반영(`app/SearchIndexService`), Elasticsearch 저장 · 색인 만들기(`out`), 목록 검색 쿼리(`app/port/ProductSearch` · `out/ElasticsearchProductSearch`)
- 남음: Kafka 수신(`in`), 목록 API(요청 변환 · 컨트롤러 · ES 장애 때 응답), `apps/search-service`, 로컬 · 서버 compose(nori 플러그인을 넣은 Elasticsearch 이미지)
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
- Elasticsearch에 연결하지 못하면 `UncheckedIOException`, 그 밖의 오류는 `ElasticsearchException`을 그대로 던진다. 몇 번 상태로 응답할지는 목록 API에서 정한다
