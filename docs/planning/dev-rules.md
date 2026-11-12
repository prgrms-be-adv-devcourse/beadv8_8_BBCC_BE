# 개발 규칙

## 6.1 API 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | `/api/v1/...` · 판매자 `/api/v1/seller/...` · 관리자 `/api/v1/admin/...` · 예외 없음 (로그인도 `/api/v1/auth/login/{provider}`) |
| 인증 | Gateway가 `Authorization: Bearer {accessToken}`을 검증하고 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname` 헤더를 붙여 넘김. 로그인이 필요한 API는 `@LoginMember AuthMember`를 받고 헤더가 없거나 잘못되면 401 · 로그인 없이 부르는 API: 로그인 · 재발급 · 로그아웃, 상품 목록 · 상세, 카테고리, FE 설정, 토스 웹훅, 리뷰 목록 · 요약, 찜 요약, 개발용 토큰(local · test · dev만) · `/seller/**`는 SELLER, `/admin/**`는 ADMIN만 (Gateway가 검사, 토큰 없으면 401 · 역할 없으면 403) |
| 성공 | 감싸지 않고 그대로 · 조회 200 · 생성 201 · 삭제 204 |
| 실패 | ProblemDetail 5필드 (`type` · `title` · `status` · `detail` · `instance`) + 오류 코드 `code` (오류 enum 상수 이름. 화면은 문구 대신 이 값으로 분기, 이미 내보낸 이름은 바꾸지 않음) · `type`은 늘 `about:blank` · detail에 다음에 할 일을 적음 · 동시 요청이 DB 제약에 막히면 409 |
| 값 | ID는 문자열 · 금액은 원 단위 정수 · 상태값 영어 대문자 |
| 시각 | `2026-10-08T10:00:00` (`LocalDateTime`, 한국 시간) · 서버 · DB 시간대 Asia/Seoul |
| 목록 | `{ content, page, size, totalElements, totalPages, hasNext }` · 커뮤니티 목록은 커서 방식(`cursor` · `size`, 마지막 쪽은 `nextCursor: null`) |

화면과 Swagger는 Gateway 주소(로컬 8083)로 붙습니다. API 문서는 **Swagger**로 작성합니다 (목록 기준: 노션 'Kidly API 명세서 v2' → v1.1부터는 develop 코드와 Swagger). API 응답은 `global`의 공통 형식을 씁니다.

**양식**

```
// 200 조회 · 201 생성(+ Location 헤더)
{
  "orderId": "1024",
  "status": "PAID",
  "totalAmount": 39000,
  "orderedAt": "2026-10-20T14:00:00"
}

// 200 목록
{
  "content": [
    { "productId": "11", "name": "데일리 맨투맨", "price": 19000 }
  ],
  "page": 0, "size": 20,
  "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

```
// 실패 (ProblemDetail 5필드 + code)
// Content-Type: application/problem+json
{
  "type": "about:blank",
  "title": "가격이 바뀌었습니다",
  "status": 409,
  "detail": "장바구니 단가와 현재 단가가 다릅니다.",
  "instance": "/api/v1/orders",
  "code": "PRICE_CHANGED"
}
```

## 6.2 이벤트 규약

**이벤트 = 이미 일어난 일을 알리는 메시지.** 보내고 기다리지 않습니다. 결과가 필요하면 받는 쪽이 새 이벤트로 알립니다.

1. **보내기** — 업무 저장과 같은 트랜잭션에서 `EventPublisher`로 발행 (롤백되면 안 나감). DB 커밋이 끝난 뒤에만 Kafka로 보냄: 주문 · 결제 · 상품 · 정산 · 자녀 프로필은 같은 트랜잭션에서 아웃박스 표에 저장하고 릴레이가 보내며, 회원 · 판매자는 커밋 뒤 바로 보냄
2. **받기** — `@KafkaListener`로 받는 도메인의 구독 그룹(`kidly.{받는도메인}`)에서 한 건씩 처리하고 처리 완료를 커밋. 실패하면 1초 간격 3번 더 시도한 뒤 `{토픽}.{받는도메인}.dlt`로 보냄(다시 해도 같은 실패는 바로 DLT). 수신 스위치를 끄면 Spring 이벤트(`@DomainEventListener`: 커밋 뒤 · 새 트랜잭션 · 동기)로 받음
3. **처리** — 같은 이벤트가 두 번 와도 한 번만 반영 (공통 표 없이 도메인마다 막음: 복사본은 `occurredAt` 비교, 처리 기록은 유니크 키(주문 · 주문상품 · 정산서 ID 등), 상태 변경은 지금 상태에서 할 수 있을 때만)
4. **결과** — 필요하면 그 트랜잭션에서 자기 결과 이벤트를 발행 (예: 재고 차감 → `stock.deducted`)

| 항목 | 규칙 |
|---|---|
| 각자 정할 것 | `eventType`(`{대상}.{과거형}`, 예: `order.created`)과 `data`(8장 표)만. eventId(UUID) · occurredAt은 보내는 쪽이 넣고, 나머지 칸은 공통 인터페이스의 기본값 |
| 값 | ID는 `Long`(Kafka 본문에서도 숫자, API 응답에서만 문자열) · 공통 칸 `eventId`는 UUID, `aggregateId` · `correlationId`는 문자열 · 금액은 원 단위 정수 · 시각은 `LocalDateTime`(한국 시간, 시간대 표시 없음) · 연락처 · 주소 · 토큰 같은 개인정보 필드는 새로 넣지 않음 (예외: `member.registered`의 email — 확인 K8) |
| 클래스 위치 | 모든 이벤트 record가 `shared/event/DomainEvent`를 구현하고, 보내는 쪽이 `shared/{도메인}/event`에 이벤트마다 record(eventId · occurredAt · data)를 만듦. 발행은 `global`의 `EventPublisher`. `eventType`은 클래스 이름에서 만들고(OrderCreatedEvent → order.created, 두 단어 대상은 재정의), `producer`는 패키지의 도메인 이름, `aggregateId`는 대상의 ID(주문 이벤트 = orderId, 자녀 정보 = childId), `correlationId` 기본값 = aggregateId, `schemaVersion` 기본 1 |
| Kafka 메시지 | 토픽은 보내는 도메인 것 하나 `kidly.{도메인}.events`(파티션 3 · 복제 1, 바꾸지 않음) · 키 = `correlationId`(순서를 지켜야 하는 흐름의 ID, 기본은 `aggregateId`. 예: 결제 · 구매확정 이벤트는 주문 ID) · 헤더 `eventType` · `eventId` 두 개(`__TypeId__` 없음) · 본문은 공통 칸 7개 + `data`의 JSON 문자열 · `data`가 null이면 보내지 않음 |
| 순서 | 같은 키는 **같은 토픽의 같은 파티션 안에서만** 순서가 보장되고, 서로 다른 토픽 사이의 순서는 보장되지 않음 — 지금 상태에서 할 수 있을 때만 반영 |
| 바꿀 때 | 보내는 쪽은 필드를 추가만 함(이름 · 타입 · 의미를 바꾸거나 지우지 않음). 추가한 필드는 없어도 동작해야 함(예전 메시지도 처리). 받는 쪽은 모르는 필드를 무시. 새 enum 값은 받는 도메인이 모두 처리하도록 배포된 뒤에 보냄 → 보내는 쪽 · 받는 쪽 어느 쪽을 먼저 배포해도 됨 |
| 침범 금지 | 다른 도메인의 토픽을 만들거나 그 토픽에 보내지 않음 · 받은 메시지를 원래 토픽에 다시 넣지 않음 · 다른 도메인의 그룹 · DLT · 오프셋을 쓰지 않음 · 특정 받는 도메인에 맞춰 이벤트를 바꾸지 않음 · 로그에는 `eventType` · `eventId` · 키만 남김(본문 금지) |
| 쓰지 않음 | `@EventListener` |

**양식** — 모든 이벤트가 같은 바깥 틀(공통 형식)을 쓰고, 이벤트마다 다른 내용은 `data`에만 넣습니다.

```
// 예시 1. 일반 이벤트 — 주문 생성됨 (Kafka 본문. 키 "1024", 헤더 eventType · eventId)
{
  "eventId": "6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90",
  "eventType": "order.created",
  "schemaVersion": 1,
  "occurredAt": "2026-10-20T14:00:00",
  "producer": "order",
  "aggregateId": "1024",
  "correlationId": "1024",
  "data": {
    "orderId": 1024,
    "memberId": 1,
    "items": [
      { "orderItemId": 5001, "optionId": 1, "quantity": 1 }
    ]
  }
}
```

```
// 예시 2. 복사본용 이벤트 — 자녀 정보 변경됨 (바뀐 뒤 전체 값, v1.1: birthDate · producer)
{
  "eventId": "0c9d7b1e-5f2a-4e8b-b3c4-7a6e1d2f9c11",
  "eventType": "child.changed",
  "schemaVersion": 1,
  "occurredAt": "2026-10-19T09:30:00",
  "producer": "childprofile",
  "aggregateId": "1",
  "correlationId": "1",
  "data": {
    "childId": 1, "memberId": 1,
    "heightCm": 115.0, "weightKg": 20.0,
    "gender": "MALE", "birthDate": "2019-01-01",
    "bodyType": "NORMAL", "preferredFit": "LOOSE",
    "deleted": false
  }
}
```

## 6.3 다른 도메인 정보가 필요할 때

다른 도메인 정보는 **이벤트 + 복사본으로만** 받습니다. 상대가 보내는 이벤트를 받아 내 테이블에 복사본을 두고 그것을 읽습니다.

**지금 쓰는 곳**

| 누가 | 복사본 | 받는 이벤트 | 쓰는 곳 |
|---|---|---|---|
| 추천 | 추천용 자녀 정보 (키 · 몸무게 · 체형 · 선호 핏 · 성별 · 생년월일) | `child.changed` | 사이즈 추천 · 없는 · 남의 · 삭제된 자녀 404 |
| 추천 | 추천용 상품 정보 (사이즈별 권장 키 · 판매 상태 · 카테고리 · 썸네일 · 등록 시각 · 품절 여부 · 사이즈 구분 없음 · 옵션 판매 상태 · 최저가 · 최종: 브랜드 · 설명 · 사이즈별 실측(#610으로 이벤트에 추가, 추천 저장도 반영(#714)) · 카테고리 경로(대분류 → 종류, 이벤트에 추가 · 추천 저장 반영(#955))) | `product.changed` | 사이즈 추천 · 자녀 맞춤 목록 |
| 주문 | 주문용 자녀 정보 (키 · 몸무게 · 체형 — 키 · 몸무게는 소수 첫째 자리, 체형은 비어 있을 수 있음 · 삭제 여부. 이벤트에 이름이 없어 주문의 자녀 이름은 null일 수 있음, 확인 K4) | `child.changed` | 주문상품에 당시 자녀 정보 복사 · 남의 자녀 거절 |
| 주문 | 주문용 상품 정보 (옵션 · 사이즈 · 단가 · 판매자 · 판매 상태 · 옵션 이름 · 옵션 판매 상태) | `product.changed` | 장바구니 담기 · 가격 확인(409) · 주문상품에 상품명 · 옵션 이름 · 사이즈 · 단가 복사 · 담기 · 주문은 옵션 판매 상태도 봄 |
| 주문 | 배송지 | (이벤트 아님) 주문 요청에 담겨 옴 | 화면이 회원 배송지 목록에서 골라 주문 요청에 넣음 — 연락처 · 주소는 이벤트로 보내지 않음 |
| 상품 · 주문 | 판매자 정보 (상호명 · 판매자 상태) | `seller.changed` | 상호명 표시 (장바구니 · 주문 상세 · 목록 · 상품 상세, 지금 상호명을 따라감) |
| 추천 | 판매자 정보 (상호명 · 판매자 상태) | `seller.changed` | 자녀 맞춤 목록의 상호명 표시 |
| 회원 | 판매자 상태 | `seller.changed` | 토큰을 발급할 때 SELLER 역할 계산 |
| 판매자 | 신청자 닉네임 | `member.changed` | 판매자 신청 목록의 신청자 이름 |
| 회원 · 자녀 프로필 | (복사본 없음) | `member.withdrawn` | 탈퇴한 회원의 배송지 · 자녀 프로필 삭제 |
| 알림 | (복사본 없음) | `member.registered` | 가입 환영 메일 (email 사용) |
| 검색 | 상품 문서 · 상호명 (Elasticsearch 색인) | `product.changed` · `seller.changed` | 상품 목록 검색 · 상호명 표시 (상품 DB를 읽지 않음) |
| 커뮤니티 | 상품 복사본 (이름 · 썸네일 · 표시 상태) · 리뷰 작성 자격 | `product.changed` · `order-item.purchase-confirmed` | 리뷰 · 찜 화면, 리뷰 작성 자격 |
| 추천 | 활동 기록 · 선호 점수 · 구매 기록 · 기준 옷(2순위) | `cart.item-added` · `order.cancelled`(구매자 취소) · `order-item.purchase-confirmed` · `search.performed` · `cart.item-removed`(예정) | AI 추천 입력 · 더보기 순서 (3.6) |
| 결제 | (복사본 없음) | `member.withdrawn` | 탈퇴 회원 지갑 동결 |
| Gateway | 토큰 무효화 목록 (메모리) | `auth.tokens-revoked` | 무효화된 토큰 거절 (도메인 아님) |

- 복사본은 **바뀐 뒤의 전체 값**을 받아 덮어씁니다 (`occurredAt`이 더 늦은 것만 반영). 삭제는 `deleted: true`로 옵니다.
- 재고는 상품만 판단합니다: `order.created`를 받으면 **재고 차감 (선점)** → `stock.deducted` 또는 `stock.deduction-failed`, `order.cancelled`를 받으면 복구. 차감 기록은 orderItemId마다 남기고 **차감됨일 때만** 복구합니다 (두 번 복구 없음). 취소된 주문은 기록해 두고, 그 뒤에 온 `order.created`는 차감하지 않습니다. 주문은 **결제대기일 때만** `stock.deducted`를 받아 결제를 요청하고, 취소된 주문이면 무시합니다. 판매자 부분 취소(`order-item.cancelled`)도 차감된 주문상품만 같은 방식으로 복구합니다(#716).
- 초기 데이터(7.1)는 각 도메인이 자기 서비스로 넣고, 그때 나간 이벤트로 다른 도메인의 복사본도 함께 생깁니다 (복사본을 따로 넣지 않음).

## 6.4 폴더 · 테스트 · Git

- **폴더 구조**는 강사님이 제공한 구조를 따릅니다. 에이전트 설정 MD 파일은 팀에 공유한 뒤 수정합니다.
- **테스트**: `test` 하위 패키지는 `src`의 도메인 단위를 따름 (안쪽 구조는 자유) · PR에는 담당 도메인의 단위 테스트와 기능 테스트를 반드시 포함 · 테스트 작성과 검수는 나눠서 · 테스트 데이터는 7.1 기준
- **Git**: PR 전에 `develop` 최신 변경을 pull해 반영 · `main` 직접 push 금지 · `develop` 직접 push는 잠금 전까지 PO만 (10/12 논의 후 잠금)
