# API 목록

도메인마다 어떤 API가 있고 무엇을 하는지 적는다. 요청 · 응답 칸과 타입은 Swagger에서 본다.

- Swagger: 서비스마다 연다(로컬 `http://localhost:<서비스 포트>/swagger-ui.html`, 포트는 README의 로컬 실행 표). 서비스에 직접 붙으므로 로그인이 필요한 API는 Gateway 헤더(`X-Member-*`)를 직접 넣어야 한다
- 오류 응답은 ProblemDetail 5필드(`type`, `title`, `status`, `detail`, `instance`)다. 도메인 오류의 상태 · `title` · `detail`은 각 도메인의 `XxxErrorCode` enum에 있다
- API를 추가 · 변경 · 삭제하는 PR에서 이 표도 같이 고친다
- 권한: 🌐 누구나 · 🔒 로그인 · 🏪 판매자 · 🛠 관리자. 판매자 · 관리자 역할은 Gateway가 `/api/v1/seller/**` · `/api/v1/admin/**` 경로로 검사한다
- 요청 수 제한: 검색어가 있는 상품 목록(`GET /api/v1/products?keyword=...`)은 Gateway가 로그인 주체(회원 · 관리자 · 자녀) · 비로그인 IP마다 분당 30번까지 받는다. 넘으면 429 ProblemDetail(`title` "요청이 너무 많습니다")과 `Retry-After`(초)다(#816)

## 인증

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/v1/auth/login/{provider}` | 소셜 로그인(카카오, 본문: 인가 코드). 처음이면 가입 | 🌐 |
| POST | `/api/v1/auth/reissue` | 토큰 재발급 | 🌐 |
| POST | `/api/v1/auth/logout` | 로그아웃 | 🌐 |
| POST | `/api/v1/auth/admin/login` | 관리자 로그인(이메일 · 비밀번호) | 🌐 |
| POST | `/api/v1/auth/admin/reissue` | 관리자 토큰 재발급 | 🌐 |
| POST | `/api/v1/auth/admin/logout` | 관리자 로그아웃 | 🌐 |
| POST | `/api/v1/auth/child/login` | 자녀 계정 로그인(아이디 · 비밀번호) | 🌐 |
| POST | `/api/v1/auth/child/reissue` | 자녀 계정 토큰 재발급 | 🌐 |
| POST | `/api/v1/auth/child/logout` | 자녀 계정 로그아웃 | 🌐 |
| GET | `/api/v1/auth/child-accounts` | 보호자가 만든 자녀 계정 목록 | 🔒 |
| POST | `/api/v1/auth/child-accounts` | 자녀 계정 만들기 | 🔒 |
| PATCH | `/api/v1/auth/child-accounts/{id}/password` | 자녀 계정 비밀번호 변경 | 🔒 |
| DELETE | `/api/v1/auth/child-accounts/{id}` | 자녀 계정 삭제 | 🔒 |
| POST | `/api/v1/admin/admins` | 관리자 계정 만들기 | 🛠 |
| POST | `/api/v1/admin/admins/{adminId}/disable` | 관리자 계정 끄기 | 🛠 |
| POST | `/api/v1/dev/tokens` | 개발용 토큰 발급(local · test · dev 프로필에서만) | 🌐 |

## 회원

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/members/me` | 내 정보 조회 | 🔒 |
| PATCH | `/api/v1/members/me` | 내 정보 수정(닉네임) | 🔒 |
| DELETE | `/api/v1/members/me` | 탈퇴. 신청하면 바로 탈퇴한다 | 🔒 |
| GET | `/api/v1/members/me/addresses` | 배송지 목록 | 🔒 |
| POST | `/api/v1/members/me/addresses` | 배송지 등록 | 🔒 |
| PATCH | `/api/v1/members/me/addresses/{addressId}` | 배송지 수정 | 🔒 |
| DELETE | `/api/v1/members/me/addresses/{addressId}` | 배송지 삭제 | 🔒 |
| PATCH | `/api/v1/members/me/addresses/{addressId}/default` | 기본 배송지 변경 | 🔒 |
| GET | `/api/v1/admin/members` | 회원 목록(필터 ALL · BUYER · SELLER · SUSPENDED · ADMIN) | 🛠 |
| POST | `/api/v1/admin/members/{memberId}/suspend` | 회원 이용 정지 | 🛠 |

## 자녀 프로필

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/v1/guardian-verification` | 본인인증 거래번호 `identityVerificationId`로 인증, 201과 Location | 🔒 |
| GET | `/api/v1/guardian-verification` | 인증 여부 `verified`와 인증 시각 `verifiedAt` 조회(미인증이면 `false`, `null`) | 🔒 |
| POST | `/api/v1/children` | 자녀 등록(만 14세 미만, 필수 동의 `requiredConsent` · 선택 동의 `optionalConsent`, 본인인증 전 403) | 🔒 |
| GET | `/api/v1/children` | 내 자녀 목록 | 🔒 |
| GET | `/api/v1/children/{childId}` | 자녀 상세 | 🔒 |
| PATCH | `/api/v1/children/{childId}` | 자녀 수정(전체 값) | 🔒 |
| DELETE | `/api/v1/children/{childId}` | 자녀 삭제(논리 삭제) | 🔒 |
| GET | `/api/v1/children/{childId}/growth-records` | 성장 이력 조회 | 🔒 |
| GET | `/api/v1/child/profile` | 자녀 계정이 자기 프로필 조회 | 🔒 |
| GET | `/api/v1/child/onboarding` | 첫 로그인 안내 확인 여부 조회 (`noticeConfirmed`) | 🔒 |
| POST | `/api/v1/child/onboarding/notice` | 첫 로그인 안내 확인 (204) | 🔒 |

## 판매자

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/v1/members/me/seller-application` | 판매자 신청(상호명 · 대표자명 · 사업자등록번호 10자리) | 🔒 |
| GET | `/api/v1/members/me/seller-application` | 내 판매자 신청 상태(가장 최근 신청) | 🔒 |
| GET | `/api/v1/seller/me` | 내 판매자 정보 | 🏪 |
| GET | `/api/v1/admin/seller-applications` | 판매자 신청 목록 | 🛠 |
| POST | `/api/v1/admin/seller-applications/{applicationId}/approve` | 판매자 승인 | 🛠 |
| POST | `/api/v1/admin/seller-applications/{applicationId}/reject` | 판매자 거절(사유) | 🛠 |
| POST | `/api/v1/admin/sellers/{sellerId}/suspend` | 판매자 자격 정지 | 🛠 |
| POST | `/api/v1/admin/sellers/changed-events` | 모든 판매자의 `seller.changed`를 지금 상태로 다시 보냄 | 🛠 |

## 상품

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/products/{productId}` | 상품 상세(판매자 상호명 포함) | 🌐 |
| GET | `/api/v1/categories` | 카테고리 트리 | 🌐 |
| GET | `/api/v1/categories/{categoryId}/attributes` | 상품 등록 때 입력할 항목(옵션 항목 · 정보 항목 · 필수 실측) | 🌐 |
| POST | `/api/v1/seller/products` | 상품 등록(판매대기로 생김. 카테고리 · 정보 항목 · 옵션 · 가격 · 실측 · 이미지) | 🏪 |
| GET | `/api/v1/seller/products` | 내 상품 목록 | 🏪 |
| GET | `/api/v1/seller/products/{productId}` | 내 상품 상세 | 🏪 |
| PUT | `/api/v1/seller/products/{productId}` | 상품 수정(version 확인. 옵션 · 가격 · 실측 교체는 판매대기 상품만) | 🏪 |
| PUT | `/api/v1/seller/products/{productId}/sale-status` | 판매 시작 · 중지(`ON_SALE` · `STOPPED`) | 🏪 |
| PUT | `/api/v1/seller/products/{productId}/delisting` | 등록 취소(판매자는 되돌릴 수 없음. 어느 판매 상태에서나 가능하고, 이미 등록 취소한 상품이면 200으로 그대로) | 🏪 |
| POST | `/api/v1/seller/options/{optionId}/inventory-adjustments` | 재고 조정(증감량 · 사유 RESTOCK · DEFECTIVE · LOST · CORRECTION) | 🏪 |
| POST | `/api/v1/seller/product-images/upload-urls` | 이미지 업로드 주소(Presigned URL) 발급 | 🏪 |
| PUT | `/api/v1/admin/products/{productId}/sale-status` | 관리자 판매 중지 · 재개 | 🛠 |
| DELETE | `/api/v1/admin/products/{productId}/delisting` | 등록 취소한 상품을 판매중지로 되돌림(등록 취소한 상품이 아니면 409) | 🛠 |
| POST | `/api/v1/admin/products/changed-events` | 판매를 시작했거나 등록 취소한 상품 전체의 `product.changed` 재발행 | 🛠 |
| GET | `/api/v1/admin/categories` | 카테고리 목록(평면 배열 + `parentId`) | 🛠 |
| POST | `/api/v1/admin/categories` | 카테고리 만들기 | 🛠 |
| PUT | `/api/v1/admin/categories/{categoryId}` | 카테고리 수정 | 🛠 |
| DELETE | `/api/v1/admin/categories/{categoryId}` | 카테고리 삭제(논리 삭제) | 🛠 |
| GET | `/api/v1/admin/product-types` | 상품 유형 목록 | 🛠 |
| POST | `/api/v1/admin/product-types` | 상품 유형 만들기 | 🛠 |
| PUT | `/api/v1/admin/product-types/{productTypeId}` | 상품 유형 수정 | 🛠 |
| DELETE | `/api/v1/admin/product-types/{productTypeId}` | 상품 유형 삭제(논리 삭제) | 🛠 |
| GET | `/api/v1/admin/attributes` | 항목 목록 | 🛠 |
| POST | `/api/v1/admin/attributes` | 항목 만들기 | 🛠 |
| DELETE | `/api/v1/admin/attributes/{attributeId}` | 항목 삭제(논리 삭제) | 🛠 |
| POST | `/api/v1/admin/attributes/{attributeId}/values` | 항목 값 추가 | 🛠 |
| DELETE | `/api/v1/admin/attributes/{attributeId}/values/{valueId}` | 항목 값 삭제(논리 삭제) | 🛠 |
| GET | `/api/v1/admin/measurement-items` | 실측 항목 목록 | 🛠 |
| POST | `/api/v1/admin/measurement-items` | 실측 항목 만들기 | 🛠 |
| DELETE | `/api/v1/admin/measurement-items/{measurementItemId}` | 실측 항목 삭제(논리 삭제) | 🛠 |

## 검색

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/products` | 상품 목록 · 검색(카테고리 · 키워드 · 항목 값 · 정렬 `NEWEST` · `PRICE_ASC` · `PRICE_DESC` · `RELEVANCE`). 검색어가 있으면 정렬과 관계없이 의미 검색(OpenAI 임베딩 · kNN)으로 뜻이 가까운 상품을 더한다. 관련도순은 검색어가 있는 상품을 먼저 보이고, 검색어가 없으면 최신순과 같다. 검색 서비스가 Elasticsearch에서 읽음 | 🌐 |

## 추천

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/recommendations/size` | 사이즈 추천(`productId` · `childId`. 추천 사이즈 · 적합도 · 안내 · 후보 사이즈 · 근거) | 🔒 |
| GET | `/api/v1/recommendations/products` | 자녀 맞춤 상품(`childId` · `categoryId` · `page` · `size` · `asOf`) | 🔒 |
| POST | `/api/v1/recommendations/product-views` | 상품 상세 진입 기록(`productId`. 활동 기록에 한 줄, 응답 204) | 🔒 |

## 주문

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/cart` | 장바구니 조회 | 🔒 |
| POST | `/api/v1/cart/items` | 장바구니 담기(같은 줄이면 수량 합산, 1 ~ 99) | 🔒 |
| PATCH | `/api/v1/cart/items/{cartItemId}` | 장바구니 수량 변경 | 🔒 |
| DELETE | `/api/v1/cart/items/{cartItemId}` | 장바구니에서 빼기 | 🔒 |
| POST | `/api/v1/orders` | 주문하기(`Idempotency-Key` 헤더 선택) | 🔒 |
| GET | `/api/v1/orders` | 내 주문 목록 | 🔒 |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 | 🔒 |
| POST | `/api/v1/orders/{orderId}/cancel` | 구매자 주문 취소(발송 전, 주문 전체) | 🔒 |
| GET | `/api/v1/orders/{orderId}/timeline` | 주문 처리 타임라인(단계 · 상태 · 시각) | 🔒 |
| POST | `/api/v1/orders/{orderId}/items/{orderItemId}/confirm` | 구매확정 | 🔒 |
| GET | `/api/v1/wish` | 공용 장바구니 조회(보호자 · 자녀가 같이 보는 자녀별 장바구니) | 🔒 |
| POST | `/api/v1/wish/items` | 공용 장바구니 담기(새 줄이면 201, 같은 옵션이면 수량을 더하고 200) | 🔒 |
| DELETE | `/api/v1/wish/items/{wishItemId}` | 공용 장바구니에서 빼기 | 🔒 |
| POST | `/api/v1/wish/requests` | 조르기 보내기 | 🔒 |
| GET | `/api/v1/wish/requests` | 조르기 목록 | 🔒 |
| GET | `/api/v1/wish/requests/{requestId}` | 조르기 상세 | 🔒 |
| POST | `/api/v1/wish/requests/{requestId}/approval` | 조르기 승인(일부 · 수량 조정) | 🔒 |
| POST | `/api/v1/wish/requests/{requestId}/rejection` | 조르기 거절(사유) | 🔒 |
| GET | `/api/v1/wish/requests/{requestId}/timeline` | 조르기 타임라인(보냄 → 승인 · 거절 → 결제한 주문) | 🔒 |
| GET | `/api/v1/seller/orders` | 판매 주문 목록 | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/ship` | 발송(택배사 · 송장번호) | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/deliver` | 배송 완료 처리 | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/cancel` | 판매자 발송 전 취소(자기 몫, 사유). 부분 취소 스위치는 기본 켜짐(#924), 끄면 409 | 🏪 |
| GET | `/api/v1/admin/orders` | 전체 주문 조회 | 🛠 |
| GET | `/api/v1/admin/orders/{orderId}/history` | 주문 상태 이력 | 🛠 |
| GET | `/api/v1/admin/orders/stuck` | 멈춘 주문 목록(`kind`) | 🛠 |
| GET | `/api/v1/admin/orders/stuck/summary` | 멈춘 주문 종류별 건수 | 🛠 |
| GET | `/api/v1/admin/orders/recoveries` | 결제 완료 반영 실패 목록 | 🛠 |
| POST | `/api/v1/admin/orders/recoveries/{recoveryId}/retry` | 결제 완료 반영 재처리 | 🛠 |
| POST | `/api/v1/admin/orders/recoveries/{recoveryId}/cancel` | 결제 완료 반영 취소 | 🛠 |
| GET | `/api/v1/admin/orders/event-failures` | 받은 이벤트 처리 실패 목록 | 🛠 |
| POST | `/api/v1/admin/orders/event-failures/{failureId}/retry` | 받은 이벤트 재처리 | 🛠 |
| POST | `/api/v1/admin/orders/event-failures/{failureId}/cancel` | 받은 이벤트 처리 취소 | 🛠 |
| GET | `/api/v1/admin/orders/dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/orders/dead-letters/{deadLetterId}` | DLT 메시지 상세(관련 주문의 지금 상태 · 권장 조치) | 🛠 |
| POST | `/api/v1/admin/orders/dead-letters/{deadLetterId}/reprocess` | DLT 메시지 재처리(원래 토픽에 다시 넣지 않음) | 🛠 |
| POST | `/api/v1/admin/orders/dead-letters/{deadLetterId}/close` | DLT 메시지 종료(처리하지 않음, 메모 필수) | 🛠 |
| GET | `/api/v1/admin/orders/outbox/quarantined` | 보내지 못하고 격리된 아웃박스 목록 | 🛠 |
| GET | `/api/v1/admin/orders/outbox/{outboxId}` | 아웃박스 상세 | 🛠 |
| POST | `/api/v1/admin/orders/outbox/{outboxId}/retry` | 격리된 아웃박스 다시 보내기 | 🛠 |
| POST | `/api/v1/admin/orders/purchase-confirmed-events` | 구매확정 이벤트 재발행 | 🛠 |
| POST | `/api/v1/admin/orders/mcp` | 주문 운영 조회 MCP 창구(읽기 전용, 스위치 기본 꺼짐) | 🛠 |

**주문 운영 조회 MCP 창구(#925)**: AI가 주문 운영 상태를 조회해 원인을 진단하는 읽기 전용 [MCP](https://modelcontextprotocol.io) 서버다. 재처리 · 종료 · 취소 같은 조작 도구는 없고, 조작은 위 관리자 API로 사람이 한다.

- 켜기: 주문 서비스 환경변수 `ORDER_MCP_ENABLED=true`(설정 `kidly.order.mcp.enabled`, 기본 false). 꺼져 있으면 경로가 없다. 목록 한 번의 줄 수 상한은 `kidly.order.mcp.max-results`(기본 50, 1~100)
- 연결: 상태 없는 Streamable HTTP(세션 없음). `POST /api/v1/admin/orders/mcp`에 JSON-RPC 본문, `Content-Type: application/json`, `Accept: application/json, text/event-stream`. 관리자 토큰으로 Gateway를 거친다. Gateway가 붙인 `X-Admin-Id`가 없거나 잘못되면 401 ProblemDetail
- 결과: 도구마다 JSON 텍스트 하나. 목록은 `{items, page, size, totalElements, totalPages, hasNext}`이고 size(기본 20)가 상한보다 크면 상한으로 줄인다. 칸은 같은 일을 하는 관리자 API 응답과 같다(ID는 문자열, 시각은 `yyyy-MM-ddTHH:mm:ss`). 없는 주문 · 잘못된 인자는 `isError: true`와 `{error, message}`
- 개인정보: 배송지 · 연락처 · 수령인 · 닉네임 · 자녀 정보는 응답에 없다. 회원 ID(`memberId` · 이력 `actorId`)는 내부 ID라 포함한다. DLT 상세는 원본 본문(`payload`) · 관리자 메모 · 예외 메시지 · 스택을 뺀 메타데이터만 주고, 실패는 예외 종류(`exceptionClass`)만 준다. 자유 문구가 될 수 있는 칸은 코드 · 예외 클래스 이름만 내보낸다(형식이 아니면 `null`, 관리자 API는 그대로):
  - 아웃박스 `lastError` · DLT `exceptionClass`: 예외 클래스 이름만(마지막 마디가 `…Exception` · `…Error`). 아웃박스 메시지 키는 0으로 시작하지 않는 숫자 · UUID만
  - 이력 `reason` · 실패 `failureCode`: 대문자로 시작하는 대문자 코드 · 결제 불일치 사유 코드(`member-mismatch` 등 목록) · `…Exception`/`…Error` 단순 이름만. 재고 실패 `reason`은 대문자 코드만
  - 이력 `source`: 경로 틀(`POST /api/v1/orders/{orderId}/cancel`) · `scheduler:작업` · 이벤트 타입만
  - DLT의 받은 값: 키 · `aggregateId` · `correlationId`는 관련 주문 ID와 같거나 UUID일 때만, `eventId`는 UUID만, `producer`는 원래 토픽 `kidly.{x}.events`의 x와 같을 때만, `eventType`(목록 · 상세)은 앞 마디가 그 토픽에 허용한 값일 때만(기본은 x 하나, `kidly.product.events`는 `product.` · `stock.`, `kidly.childprofile.events`는 `child.`, `kidly.auth.events`는 `child-account.`(주문이 건너뛰는 `auth.tokens-revoked`는 `null`). 토픽이 그 형식이 아니면 둘 다 `null`). 재처리 결과(`lastReprocessResult`)는 '자체 보관으로 넘김'이면 `HANDED_OFF`, 그 밖은 예외 클래스 이름만

| 도구 | 인자 | 같은 관리자 API(`/api/v1/admin/orders` 아래) |
|---|---|---|
| `stuck_orders_summary` | 없음 | `GET /stuck/summary` |
| `stuck_orders` | `kind`(필수) · `page` · `size` | `GET /stuck` |
| `order_timeline` | `orderId`(필수) | `GET /{orderId}/history`. 이벤트가 바꾼 상태는 `actorType` EVENT · `source` 이벤트 타입. 상한보다 길면 최근 것만(`truncated`) |
| `dead_letters` | `status` · `failureType` · `eventType` · `page` · `size` | `GET /dead-letters` |
| `dead_letter_detail` | `deadLetterId`(필수) | `GET /dead-letters/{deadLetterId}`(본문 · 메모 · 예외 메시지 · 스택 제외) |
| `event_failures` | `status` · `page` · `size` | `GET /event-failures` |
| `payment_recoveries` | `status` · `page` · `size` | `GET /recoveries` |
| `outbox_quarantined` | `page` · `size` | `GET /outbox/quarantined` |

조르기(`/api/v1/wish/**`)는 회원 로그인과 자녀 로그인(자녀 토큰, #906)을 함께 받는다. 자녀는 자기 프로필의 공용 장바구니 담기 · 빼기 · 조회와 조르기 보내기 · 목록 · 상세 · 타임라인만 한다(다른 자녀 것이면 403). 승인 · 거절은 보호자(회원)만 한다(자녀면 403). 자녀 계정 연결을 확인할 수 없으면(연결 없음 · 삭제 · 보호자 다름) 403이다. 주문 · 장바구니 · 취소 등 다른 주문 API는 회원 로그인만 받는다.

## 결제

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/wallets/me` | 예치금 잔액 | 🔒 |
| GET | `/api/v1/wallets/me/ledgers` | 예치금 입출금 내역 | 🔒 |
| POST | `/api/v1/wallets/me/charges` | 충전 요청 생성(30분 뒤 만료) | 🔒 |
| GET | `/api/v1/wallets/me/charges` | 충전 내역 | 🔒 |
| GET | `/api/v1/wallets/me/charges/{chargeId}` | 충전 상세 | 🔒 |
| POST | `/api/v1/wallets/me/charges/confirm` | 충전 승인(토스) | 🔒 |
| POST | `/api/v1/wallets/me/charges/fail` | 결제창 실패 · 취소를 충전 실패로 반영 | 🔒 |
| POST | `/api/v1/wallets/me/charges/{chargeId}/cancel` | 충전 취소 | 🔒 |
| GET | `/api/v1/payments` | 주문 결제 · 환불 내역 | 🔒 |
| GET | `/api/v1/payments/{paymentId}` | 주문 결제 · 환불 상세 | 🔒 |
| POST | `/api/v1/webhooks/toss` | 토스 웹훅. 토스에서 직접 취소한 충전을 맞춤 | 🌐 |
| GET | `/api/v1/admin/payments` | 전체 결제 · 환불 조회 | 🛠 |
| GET | `/api/v1/admin/wallets` | 지갑 목록(`frozen` · `hasBalance`로 거름) | 🛠 |
| POST | `/api/v1/admin/wallets/{walletId}/return-balance` | 동결 지갑의 잔액을 환급했다고 기록하고 0으로 만듦(송금은 하지 않음) | 🛠 |

## 정산

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/seller/settlements` | 내 정산서 목록 | 🏪 |
| GET | `/api/v1/seller/settlements/{settlementId}` | 정산서 상세 | 🏪 |
| GET | `/api/v1/seller/settlement-targets` | 정산 대상 조회 | 🏪 |
| POST | `/api/v1/admin/settlements/run` | 정산 수동 실행 | 🛠 |
| GET | `/api/v1/admin/settlements` | 판매자별 정산 조회 | 🛠 |
| POST | `/api/v1/admin/settlements/{settlementId}/retry` | 결과를 못 받은 `COMPLETED` 정산서의 지급 요청 재발행 | 🛠 |
| GET | `/api/v1/admin/settlement-runs/{runId}` | 정산 실행 조회 | 🛠 |
| POST | `/api/v1/admin/settlement-runs/{runId}/recover` | 서버가 멈춰 실행 중으로 남은 실행을 실패로 정리 | 🛠 |
| GET | `/api/v1/admin/settlement-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/settlement-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/settlement-dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |

## 커뮤니티

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/reviews/eligible` | 리뷰를 쓸 수 있는 주문상품(커서 목록) | 🔒 |
| POST | `/api/v1/reviews` | 리뷰 작성 | 🔒 |
| PATCH | `/api/v1/reviews/{reviewId}` | 리뷰 수정(보낸 필드만, 작성자만) | 🔒 |
| DELETE | `/api/v1/reviews/{reviewId}` | 리뷰 삭제(작성자만) | 🔒 |
| GET | `/api/v1/reviews` | 상품별 리뷰 목록(`productId`, 최신순) | 🌐 |
| GET | `/api/v1/reviews/me` | 내 리뷰 | 🔒 |
| GET | `/api/v1/reviews/summary` | 리뷰 요약(리뷰 수 · 평균 별점) | 🌐 |
| PUT | `/api/v1/favorites/{productId}` | 찜(다시 해도 1건) | 🔒 |
| DELETE | `/api/v1/favorites/{productId}` | 찜 취소(없어도 204) | 🔒 |
| GET | `/api/v1/favorites` | 내 찜 목록. `productIds`를 주면 그중 찜한 것만 | 🔒 |
| GET | `/api/v1/favorites/summary` | 찜 요약(찜 수) | 🌐 |
| GET | `/api/v1/admin/community/dead-letters` | DLT에 남은 메시지 목록 | 🛠 |
| GET | `/api/v1/admin/community/dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/community/dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |

## 알림

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/notifications` | 내 알림함(최신순, `page` · `size` 1~100, 기본 0 · 20) | 🔒 |
| GET | `/api/v1/notifications/unread-count` | 안 읽은 내 알림 개수 `{ count }` | 🔒 |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 읽음 처리(204, 다시 해도 204). 없거나 남의 알림은 404 | 🔒 |
