# 검색 도메인 (#526)

상품 목록 검색(`GET /api/v1/products`)을 상품에서 떼어 맡는다. 상품 · 판매자가 발행한 이벤트로만 색인하고, 상품 DB를 읽지 않는다.

## 진행 상태
- 됨: 모듈, 검색 문서 계산(`domain/SearchProduct`), 상호명(`domain/SearchSeller`), 이벤트 반영(`app/SearchIndexService`), Elasticsearch 저장 · 색인 만들기(`out`)
- 남음: 목록 검색 쿼리(`out`), Kafka 수신(`in`), 목록 API, `apps/search-service`, 로컬 · 서버 compose(nori 플러그인을 넣은 Elasticsearch 이미지)
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
