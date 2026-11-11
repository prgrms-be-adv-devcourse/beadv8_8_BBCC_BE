# API 목록

도메인마다 어떤 API가 있고 무엇을 하는지 적는다. 요청 · 응답 칸과 타입은 Swagger에서 본다.

- Swagger: 서비스마다 연다(로컬 `http://localhost:<서비스 포트>/swagger-ui.html`, 포트는 README의 로컬 실행 표). 서비스에 직접 붙으므로 로그인이 필요한 API는 Gateway 헤더(`X-Member-*`)를 직접 넣어야 한다
- 오류 응답은 ProblemDetail 5필드(`type`, `title`, `status`, `detail`, `instance`)다. 도메인 오류의 상태 · `title` · `detail`은 각 도메인의 `XxxErrorCode` enum에 있다
- API를 추가 · 변경 · 삭제하는 PR에서 이 표도 같이 고친다
- 권한: 🌐 누구나 · 🔒 로그인 · 🏪 판매자 · 🛠 관리자 · 내부(서비스끼리만 부름. Gateway가 `/internal/**`을 밖에서 404로 막는다). 판매자 · 관리자 역할은 Gateway가 `/api/v1/seller/**` · `/api/v1/admin/**` 경로로 검사한다
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
| GET | `/api/v1/admin/auth-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/auth-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/auth-dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |
| POST | `/api/v1/dev/tokens` | 개발용 토큰 발급(local · test · dev 프로필에서만) | 🌐 |

## 회원

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/members/me` | 내 정보 조회 | 🔒 |
| PATCH | `/api/v1/members/me` | 내 정보 수정(닉네임) | 🔒 |
| DELETE | `/api/v1/members/me` | 탈퇴. 판매자에게 남은 주문·정산이 있으면 409, 확인할 수 없으면 503 | 🔒 |
| GET | `/api/v1/members/me/addresses` | 배송지 목록 | 🔒 |
| POST | `/api/v1/members/me/addresses` | 배송지 등록 | 🔒 |
| PATCH | `/api/v1/members/me/addresses/{addressId}` | 배송지 수정 | 🔒 |
| DELETE | `/api/v1/members/me/addresses/{addressId}` | 배송지 삭제 | 🔒 |
| PATCH | `/api/v1/members/me/addresses/{addressId}/default` | 기본 배송지 변경 | 🔒 |
| GET | `/api/v1/admin/members` | 회원 목록(필터 ALL · BUYER · SELLER · SUSPENDED · ADMIN) | 🛠 |
| POST | `/api/v1/admin/members/{memberId}/suspend` | 회원 이용 정지 | 🛠 |
| POST | `/api/v1/admin/members/registered-events` | 이메일이 있는 회원마다 `member.registered`를 `resent=true`로 다시 보냄(알림 이메일 복사본 채우기). 응답 `publishedCount` | 🛠 |
| GET | `/api/v1/admin/member-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/member-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/member-dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |

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
| GET | `/api/v1/admin/child-profile-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/child-profile-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/child-profile-dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |
| POST | `/api/v1/admin/children/changed-events` | 삭제되지 않은 자녀마다 `child.changed`를 지금 값으로 다시 보냄(인증 · 주문 · 추천 자녀 복사본 채우기). 응답 `publishedCount` | 🛠 |
| POST | `/api/v1/admin/children/growth-recorded-events` | 삭제되지 않은 자녀의 성장 이력을 `child.growth-recorded`로 다시 보냄. 응답 `publishedCount` | 🛠 |

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
| GET | `/api/v1/admin/seller-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/seller-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/seller-dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |

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
| GET | `/api/v1/products` | 상품 목록 · 검색(카테고리 · 키워드 · 항목 값 · 정렬 `NEWEST` · `PRICE_ASC` · `PRICE_DESC` · `RELEVANCE`). 쿼리 `childId`(선택, 보호자가 고른 자녀 프로필 ID)는 결과를 바꾸지 않고 `search.performed`에만 담긴다(회원의 자녀인지는 받는 쪽이 확인). 검색어가 있으면 정렬과 관계없이 의미 검색(OpenAI 임베딩 · kNN)으로 뜻이 가까운 상품을 더한다. 관련도순은 검색어가 있는 상품을 먼저 보이고, 검색어가 없으면 최신순과 같다. 검색 서비스가 Elasticsearch에서 읽음 | 🌐 |
| POST | `/api/v1/search/clicks` | 검색 결과 클릭 기록(본문 `keyword` · `productId` · `rank`(보여 준 목록에서의 위치, 1부터) · `sort`(목록 정렬, 없으면 `NEWEST`) · `childId`(선택, 고른 자녀 프로필 ID)). 저장하지 않고 `search.result-clicked` 이벤트로 보낸다. 응답 204 | 🔒 |

## 추천

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/recommendations/size` | 사이즈 추천(`productId` · `childId`. 추천 사이즈 · 적합도 · 안내 · 후보 사이즈 · 근거 `reasons`(`type` · `detail` · `sourceId`) · 출처 `source`(`AI` · `RULE`)). AI가 규칙 계산 결과를 받아 그 상품의 실제 사이즈 안에서 고른다. AI가 없거나 실패 · 2.5초 초과 · 하루 호출 상한 · 상품에 없는 사이즈를 답하면 규칙 결과로 답하고 `source`는 `RULE`, `reasons`는 `[]`이다. AI 답은 규칙 후보 밖의 사이즈일 수 있어 `recommendedSize`가 `candidateSizes`에 없을 수 있다. 사이즈 구분이 없는 상품은 AI를 부르지 않는다. 같은 자녀 · 상품 · 입력이면 24시간 동안 지난 AI 답을 다시 쓴다 | 🔒 |
| GET | `/api/v1/recommendations/products` | 자녀 맞춤 상품(`childId` · `categoryId` · `page` · `size` · `asOf`). 선호 점수 순이고, 응답 끝에 기준 시각 `asOf`(한국 시간, 시간대 표시 없음, 초 단위)가 있다. 2쪽부터는 1쪽 응답의 `asOf`를 그대로 보낸다. 없으면 지금 시각이고, 지금보다 늦으면 400 | 🔒 |
| GET | `/api/v1/recommendations/products/preview` | 홈 맛보기 4개(`childId`). 응답 `source`(`AI` · `RULE`)와 `products`(더보기 카드 칸 + `reason`). AI가 자녀 맞춤 상품 상위 20개 중 4개와 이유를 고르고, 같은 입력이면 24시간 동안 지난 답을 다시 쓴다. AI를 쓸 수 없으면(키 없음 · 하루 상한 · 실패 · 시간 초과) 상위 4개를 `RULE`로, 이유는 `null`. `source`가 `AI`여도 `reason`은 `null`일 수 있다(AI가 모자라게 골라 빈 자리를 채운 상품, AI가 이유를 주지 않은 상품). 후보가 없으면 `products`는 `[]`, 없는 자녀 · 남의 자녀 · 삭제된 자녀면 404 | 🔒 |
| GET | `/api/v1/recommendations/companions` | 함께 사면 좋은 상품(`productId` · `childId` 필수). 보고 있는 상품과 짝이 되는 종류(예: 상의 → 바지 · 치마 · 아우터 · 잡화. 카테고리 「하의」와 「바지」는 같은 종류) 가운데 자녀에게 맞는 사이즈로 살 수 있고 장바구니 · 구매확정 상품이 아닌 상품을 4개까지 돌려준다. 응답 `source`(`AI` · `RULE`)와 `products`(상품 카드 · AI가 쓴 이유 `reason`). `reason`은 40자 안의 경어체 한 문장이고, 규칙으로 채운 상품이거나 AI 이유가 40자를 넘거나 경어체로 끝나지 않으면 `null`이다. 후보가 없으면 `products`는 `[]`, 없는 상품 · 없는 자녀 · 남의 자녀 · 삭제된 자녀면 404 | 🔒 |
| POST | `/api/v1/recommendations/product-views` | 상품 상세 진입 기록(`productId` · 선택 `childId`. 같은 회원 · 자녀 · 상품은 같은 날(한국 시간) 한 번만 기록, 응답 204, 없는 자녀 · 남의 자녀 · 삭제된 자녀면 404) | 🔒 |
| GET | `/api/v1/recommendations/children/{childId}/reference-garments` | 자녀의 기준 옷 목록(상의, 바지, 아우터, 원피스, 치마 순서이고 없으면 `[]`. 칸 `part`(`TOP` · `BOTTOM` · `OUTER` · `DRESS` · `SKIRT`) · `categoryId` · `measurements`(실측 항목 코드별 cm)). 남의 자녀면 403, 없는 자녀 · 삭제된 자녀면 404 | 🔒 |
| PUT | `/api/v1/recommendations/children/{childId}/reference-garments/{part}` | 기준 옷 저장(`{part}`는 `TOP` 상의 · `BOTTOM` 바지 · `OUTER` 아우터 · `DRESS` 원피스 · `SKIRT` 치마. 본문 `categoryId`(상품 말단 카테고리. 상의는 「상의」, 바지는 「하의」 · 「바지」, 아우터는 「아우터」, 원피스는 「원피스」, 치마는 「치마」 카테고리) · `measurements`(`TOP` · `OUTER` 필수 `TOTAL_LENGTH` · `CHEST_WIDTH`, 선택 `SHOULDER_WIDTH` · `SLEEVE_LENGTH` / `DRESS` 필수 `TOTAL_LENGTH` · `CHEST_WIDTH`, 선택 `SHOULDER_WIDTH` / `BOTTOM` 필수 `TOTAL_LENGTH` · `WAIST_WIDTH`, 선택 `RISE` / `SKIRT` 필수 `TOTAL_LENGTH` · `WAIST_WIDTH`, 선택 `HIP_WIDTH`. 0cm 초과 200cm 이하, 소수 첫째 자리까지). 원피스 · 아우터를 `TOP`에, 치마를 `BOTTOM`에 저장하면 400이다. 같은 구분을 다시 저장하면 교체하고 응답 200에 저장한 값. 값이 틀리거나 알 수 없는 구분이면 400, 남의 자녀면 403, 없는 자녀 · 삭제된 자녀면 404 | 🔒 |
| DELETE | `/api/v1/recommendations/children/{childId}/reference-garments/{part}` | 기준 옷 삭제(응답 204, 이미 없어도 204. 알 수 없는 구분이면 400, 남의 자녀면 403, 없는 자녀 · 삭제된 자녀면 404) | 🔒 |
| GET | `/api/v1/recommendations/children/{childId}/purchases?page=&size=` | 자녀에게 산 옷 목록(기준 옷 「산 옷에서 고르기」용, #1109). 구매확정된 주문상품만 구매확정이 늦은 순서로, 팀 목록 형식. 칸 `orderItemId` · `productId` · `productName` · `categoryId` · `size`(주문 당시 사이즈) · `measurements`(그 사이즈의 실측, 실측 항목 코드별 cm) · `confirmedAt`. 상품 정보가 없으면 `productName` · `categoryId` · `measurements`가 null, 그 사이즈의 실측이 없으면 `measurements`만 null. `size`(쪽 크기)는 1 ~ 100(기본 20). 남의 자녀면 403, 없는 자녀 · 삭제된 자녀면 404 | 🔒 |

## 주문

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/cart` | 내 장바구니 조회(회원 · 로그인한 자녀 각자, 줄마다 `reserved`) | 🔒 |
| POST | `/api/v1/cart/items` | 장바구니 담기(같은 줄이면 수량 합산, 1 ~ 99). 자녀는 입을 아이가 자기로 고정 | 🔒 |
| PATCH | `/api/v1/cart/items/{cartItemId}` | 장바구니 수량 변경(예약된 칸 409) | 🔒 |
| DELETE | `/api/v1/cart/items/{cartItemId}` | 장바구니에서 빼기(예약된 칸 409) | 🔒 |
| POST | `/api/v1/orders` | 주문하기(`Idempotency-Key` 헤더 선택). 줄마다 장바구니 칸 `cartItemId`(선택, #998): 내 칸이거나 결제를 허락받은 예약 칸만(같은 가족의 허락받지 않은 칸 403, 다른 가족의 칸은 없는 칸과 같게 404). 허락받은 예약 칸이라도 그 예약의 결제 가능 기한이 지났으면 409 `RESERVATION_EXPIRED`(`결제 기한이 지난 예약입니다`, #1008). 허락받은 예약 칸은 담긴 수량까지만 주문할 수 있다(넘으면 409 `RESERVED_QUANTITY_EXCEEDED` `예약된 수량보다 많이 주문할 수 없습니다`, 내 칸은 수량 제한 없음. 기한도 지났으면 `RESERVATION_EXPIRED`가 먼저). 결제가 끝나면 그 칸에서 산 수량만큼 빠지고(다 빠지면 칸이 지워진다, 결제한 사람이 칸 주인의 보호자와 달라도) `cart.item-purchased`(`quantity` = 산 수량)가 나간다(예약이 먼저 풀려도. 칸이 남으면 예약은 그대로. `cart.item-removed`는 사용자가 뺄 때만) | 🔒 |
| GET | `/api/v1/orders` | 내 주문 목록 | 🔒 |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 | 🔒 |
| GET | `/internal/sellers/{sellerId}/order-items/summary` | 서비스 사이 전용(#1120, 판매자 탈퇴 #1091의 남은 주문 확인). 200 `{"sellerId": 12, "openItemCount": 3}`: 그 판매자 상품의 주문상품 중 주문이 취소되지 않았고 주문상품이 `ORDERED`인 수(결제대기 · 결제완료 · 배송 준비 · 배송 중 · 배송 완료 후 구매확정 전. 구매확정 · 취소된 주문상품과 전체 취소된 주문의 주문상품은 빠짐, 주문이 없으면 0). `sellerId`는 숫자다(주문의 다른 응답은 ID가 문자열). 같은 모양의 계약 `shared.order.out.SellerOrderItems`(`summarize`)를 주문이 구현한다. 판매자 ID가 숫자가 아니거나 1 미만이면 400 `잘못된 판매자 ID입니다`. 인증 헤더를 요구하지 않고 밖에서는 Gateway가 `/internal/**`을 막는다(404) | 내부 |
| POST | `/api/v1/orders/{orderId}/cancel` | 구매자 주문 취소(발송 전, 주문 전체) | 🔒 |
| GET | `/api/v1/orders/{orderId}/timeline` | 주문 처리 타임라인(단계 · 상태 · 시각) | 🔒 |
| POST | `/api/v1/orders/{orderId}/items/{orderItemId}/confirm` | 구매확정 | 🔒 |
| GET | `/api/v1/orders/items/mine` | 나를 위해 산 상품 내역(자녀 로그인만, 최신순 · 목록 형식, 출처 칸 없음, #1000) | 🔒 |
| POST | `/api/v1/wish/requests` | 조르기 보내기(자녀만, 내 장바구니 칸 `cartItemIds` + 메시지. 칸을 예약한다) | 🔒 |
| GET | `/api/v1/wish/requests` | 조르기 목록(내가 보낸 것 + 내가 승인자인 것, `childId`로 거르기 선택) | 🔒 |
| GET | `/api/v1/wish/requests/{requestId}` | 조르기 상세(칸 · 산 것 `purchases` · 결제된 주문상품 ID `paidOrderItemIds`) | 🔒 |
| POST | `/api/v1/wish/requests/{requestId}/rejection` | 조르기 거절(승인자만, 사유. 예약이 풀린다) | 🔒 |
| POST | `/api/v1/wish/requests/{requestId}/cancellation` | 조르기 철회(보낸 사람만, 처리 전에만. 상태 `CANCELLED` · `cancelReason` DIRECT, 예약이 풀리고 칸은 장바구니에 남음, #968) | 🔒 |
| GET | `/api/v1/wish/requests/{requestId}/timeline` | 조르기 타임라인(보냄 → 결제 완료 · 거절 · 철회 · 만료(시스템), 산 주문 ID `orderIds`, 직접 철회면 `cancelledBy`) | 🔒 |
| GET | `/api/v1/seller/orders` | 판매 주문 목록. 주문상품에 자녀 정보(`child`)는 없다(#997, 제3자 제공 동의 없이 판매자에게 넘기지 않음) | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/ship` | 발송(택배사 · 송장번호) | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/deliver` | 배송 완료 처리(처음 완료 때 `delivery.delivered` 1번: 그 몫 주문상품(취소 제외) · 자동 구매확정 예정 시각, #1061. 택배 조회 완료도 같다) | 🏪 |
| POST | `/api/v1/seller/orders/{sellerOrderId}/cancel` | 판매자 발송 전 취소(자기 몫, 사유). 부분 취소 스위치는 기본 켜짐(#924), 끄면 409. 판매자가 판매할 수 없게 되면(#1121, 판매자 상태가 ACTIVE 아님) 시스템이 그 판매자의 배송 전 주문을 정리한다: 결제대기 주문은 전체 취소(`cancelReason` `SELLER_UNAVAILABLE`), 결제완료 · 배송 준비 몫은 이 취소와 같은 결과(주문상품 `cancelReason` `SELLER_UNAVAILABLE`, 판매자가 고르는 사유는 아님. 그 몫으로 주문 전체가 취소되면 주문 `cancelReason`도 `SELLER_UNAVAILABLE`), 배송 중 · 배송 완료는 그대로 | 🏪 |
| GET | `/api/v1/admin/orders` | 전체 주문 조회 | 🛠 |
| GET | `/api/v1/admin/orders/{orderId}/history` | 주문 상태 이력 | 🛠 |
| GET | `/api/v1/admin/orders/stuck` | 멈춘 주문 목록(`kind`). 줄마다 AI 진단 메모 `aiMemo`(#926, 없으면 null) | 🛠 |
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

장바구니(`/api/v1/cart/**`, #998)는 회원 로그인과 자녀 로그인(자녀 토큰)을 함께 받고, 각자 "내 장바구니"를 쓴다. 자녀 장바구니는 그 자녀만 보고 바꾼다: 보호자 · 형제가 그 칸을 바꾸면 403, 다른 회원이면 404. 자녀가 담을 때 입을 아이는 자기로 고정이고(다른 자녀를 고르면 400), 자녀 프로필이 아직 없으면 담을 수 없다(404). 자녀 계정 연결을 확인할 수 없으면 403이다. 다른 곳(조르기 등)이 예약한 칸은 빼거나 수량을 바꾸면 409다(`reserved` true). 결제가 끝나 예약 칸에서 산 수량만큼 빠지면(칸이 남는 일부 수량 포함) 장바구니는 그 예약에 결제 정리 시각을 남기고, 예약한 쪽은 이를 보고 이미 산 것이 있는지 안다(조르기 거절 · 철회 409).

조르기(`/api/v1/wish/requests/**`, #999)는 "구매 요청"이다: 승인 단계 없이 **승인 = 결제**다. 회원 로그인과 자녀 로그인(자녀 토큰, #906)을 함께 받는다.

- 보내기: 자녀만(회원이면 403). 자기 장바구니(`/api/v1/cart`) 칸을 골라 보내면 그 칸이 예약된다(결제 허용 = 승인자). 진행 중인 조르기에 든 칸은 넣을 수 없고(409), 남의 칸 · 없는 칸은 404, 다른 곳이 예약한 칸은 409다. 성공하면 201 · `wish.sent`
- 승인자: 저장하지 않고 그때 정한다. 지금은 보낸 자녀의 보호자 1명이다. 볼 수 있는 사람은 보낸 사람 + 승인자이고, 같은 가족의 다른 자녀는 403, 그 밖은 404다
- 승인하고 결제: 조르기 API는 없다. 승인자가 주문하기(`POST /api/v1/orders`)의 줄마다 예약된 칸 `cartItemId`를 넣어 주문한다(일부 칸만 · 수량 조정 가능). 결제가 끝나면 그 칸에서 산 수량만큼 빠지고 조르기가 `PAID`(결제한 사람 `payerType` · `payerId` · `paidAt`, 결제한 사람은 칸의 결제 회원이다)가 되며, 주문하지 않은 칸의 예약은 풀린다. 일부 수량만 결제해도 그 조르기는 결제 완료이고, 남은 칸은 예약이 풀려 자녀 장바구니에 남는다. 결제되지 않으면(잔액 부족 등) 대기(`REQUESTED`) 그대로다. 결제 완료 반영은 Kafka로 받아 비동기다(잠깐 늦을 수 있다)
- 거절: 승인자만(아니면 403), 사유 하나. 철회(#968): 보낸 사람만(아니면 403). 둘 다 처리 전에만(처리된 조르기면 409) 되고 예약을 푼다(칸은 장바구니에 남아 다시 조를 수 있다). 결제가 끝났는데 아직 조르기에 반영되기 전이면(결제로 빠진 칸이 있거나, 칸이 남는 일부 수량 결제라도 결제 정리가 끝났으면) 409(`결제가 반영 중인 조르기입니다`)다
- 보호자가 주문한 뒤의 철회 · 거절은 결제를 막지 않는다. 결제되면 칸은 정리되고, 조르기는 철회 · 거절 상태로 남는다
- 산 것 `purchases[]`는 결제로 장바구니에서 빠진 칸이다(`orderId` · `cartItemId` · `quantity` · `purchasedAt`). `quantity`는 그 칸에서 이번에 산 수량이다(칸에 남은 수량 아님)
- 결제된 주문상품 ID `paidOrderItemIds[]`(#1000, 목록 · 상세 모두, 문자열, 먼저 산 것부터): 이 조르기로 결제된 주문상품이다. 아래 나를 위해 산 상품 내역의 `orderItemId`와 맞춰 화면이 출처를 표시한다. 결제 완료 반영과 같이 비동기라 결제 직후에는 잠시 비어 있을 수 있다
- 사람 종류 값이 바뀌었다(계약 변경, #999): 보낸 사람 `requestedByType`(예전 `requestedBy`) · 타임라인 `cancelledBy`는 `MEMBER` · `CHILD`다(예전 `PARENT`는 `MEMBER`로 바뀌고 옛 데이터도 옮겼다)
- 자녀 · 자녀 계정 삭제(#1001): 그 자녀 장바구니 칸이 지워지고(예약 칸 포함) 그 자녀가 보낸 진행 중 조르기는 `CANCELLED`(원인 `CHILD_DELETED` · `ACCOUNT_DELETED`)가 된다. 회원 탈퇴는 결제대기 주문을 취소하고 회원 장바구니를 비운다(결제 · 배송 중 주문은 그대로). 정리로 빠진 칸은 `cart.item-removed`를 내지 않는다
- 기한 만료(#1008, BEG-05): 보낸 뒤 기한(기본 7일, `kidly.order.wish.expiry.ttl`) 안에 결제 · 거절이 없으면 시스템이 `EXPIRED`(`expiredAt`)로 만료하고 예약을 푼다(칸은 장바구니에 남는다, `wish.expired`). 기한은 저장하지 않고 보낸 시각 + 지금 설정 기한으로 계산한다: 응답 `expiresAt`은 대기 중이고 만료가 켜져 있을 때만 있고 그 밖은 null이다. 기한을 바꾸면 대기 중인 조르기에도 바로 적용되고, 끄면 만료되지 않는다. 기한이 지난 조르기의 거절 · 철회는 만료 처리 전이라도 409(`기한이 지난 조르기입니다`)다. 보호자의 결제(그 예약 칸으로 주문하기)도 만료 처리 전이라도 409(`결제 기한이 지난 예약입니다`)다: 보낼 때 장바구니 예약에 결제 가능 기한(= 만료 기한, 꺼져 있으면 없음)을 두고, 기한 설정을 바꾸거나 끄면 서버가 뜰 때 진행 중 조르기의 예약 기한을 다시 맞춘다(설정은 기동 때만 바뀐다). 기한이 지났지만 만료 처리(기한 + 유예가 지난 뒤의 주기 작업, 기본 1시간마다, 기동 뒤 첫 실행도 한 주기 뒤) 전이면 칸 예약이 유지되므로, 같은 칸으로 다시 조르면 409(`조르는 중인 상품입니다`)다. 기한이 지난 뒤 유예 + 다음 주기 전까지는 `REQUESTED`로 남고, 이때 `expiresAt`은 지난 시각이며 거절 · 철회 · 새 주문 · 같은 칸 다시 조르기는 모두 409다. 결제가 반영 중인 조르기(결제로 빠진 칸이 있거나, 칸이 남는 일부 수량 결제라도 구매 표시가 있는데 아직 반영 전)는 만료하지 않는다. 만료 처리는 기한이 지나고도 유예(기본 15분, `kidly.order.wish.expiry.grace`, 결제 마감보다 길게)가 더 지나야 한다: 기한이 지나면 새 주문은 막히지만(409), 기한 전에 만든 주문이 결제 마감 안에 결제되면 유예 동안 반영돼 `PAID`가 되고, 아니면 유예 뒤 만료된다(응답 `expiresAt` · 거절/철회 409는 유예 없이 기한 기준). 만료와 결제가 겹치면 먼저 저장한 쪽이 이기고, 만료가 이기면 그 결제는 조르기와 이어지지 않는다(산 것 `purchases`에 남지 않고 상태 · 이벤트 그대로)
- 상태는 `REQUESTED` · `PAID` · `REJECTED` · `CANCELLED`(원인 `cancelReason` DIRECT · CHILD_DELETED · ACCOUNT_DELETED · SYSTEM) · `EXPIRED`(#1008)다. #999 전 조르기는 옮기면서 진행 중이던 것을 `CANCELLED`(SYSTEM)로 바꿨다. 공용 장바구니(`/api/v1/wish` · `/api/v1/wish/items`)와 승인(`/approval`)은 없앴다
- 자녀 계정 연결을 확인할 수 없으면(연결 없음 · 삭제 · 보호자 다름) 403이다. 주문 · 취소 등 다른 주문 API는 회원 로그인만 받는다(나를 위해 산 상품 내역만 자녀 로그인)

나를 위해 산 상품 내역(`GET /api/v1/orders/items/mine?page=&size=`, #1000, BEG-07)은 자녀 로그인만 받는다(회원 로그인이면 403 `자녀만 볼 수 있습니다`, 보호자는 주문 목록으로 본다. 헤더 없음 401, 자녀 계정 연결을 확인할 수 없으면 403). 자기 프로필이 입을 아이로 지정된 주문상품을 주문 일시 최신순으로 팀 목록 형식(`content` · `page` · `size` · `totalElements` · `totalPages` · `hasNext`)으로 준다. `size`는 1~100(기본 20).

- 대상: 결제된 적 있는 주문의 주문상품만. 결제대기 · 결제 없이 취소된 주문은 빠지고, 결제 뒤 취소는 `CANCELLED`로 보인다
- 한 줄: `orderItemId` · `orderedAt` · `productId` · `productName` · `optionName` · `quantity` · `status`
- 상태 `status`: `PAID`(결제 완료) · `SHIPPING`(배송 중, 그 판매자 배송이 발송됨) · `DELIVERED`(배송 완료) · `CONFIRMED`(구매확정) · `CANCELLED`(주문상품 또는 주문 취소)
- 빼는 값: 가격 · 금액 · 배송지 · 판매자 · 주문 ID(주문 상세 링크) · 출처
- 출처(조르기로 산 것 / 보호자가 산 것)는 화면이 합친다: 그 자녀의 조르기 목록(`GET /api/v1/wish/requests`)의 `paidOrderItemIds`를 모아 이 목록의 `orderItemId`와 맞추고, 있으면 조르기로 산 것, 없으면 보호자가 산 것으로 표시한다. 조르기 반영이 비동기라 결제 직후에는 잠시 보호자가 산 것으로 보일 수 있다. 주문은 조르기를 모르고 조르기는 주문을 읽지 않는다

**멈춘 주문 AI 진단 메모(#926)**: 멈춘 주문 목록(`GET /api/v1/admin/orders/stuck`)의 줄마다 `aiMemo`가 붙는다(필드 추가). 주기 작업이 새로 멈춘 주문을 AI에 물어 남긴 원인 요약 · 권장 조치이고, AI의 제안일 뿐 조치는 관리자가 위 API로 한다. (주문, 멈춘 종류)별 가장 최근 성공 메모이며 없거나 실패만 있으면 `null`.

| `aiMemo` 칸 | 설명 |
|---|---|
| `summary` | 원인 요약 |
| `recommendedAction` | 권장 조치 |
| `confidence` | `LOW` · `MEDIUM` · `HIGH` |
| `provider` | 답한 곳(OpenAI 호환 주소의 호스트, 예: `api.groq.com` · 가짜면 `fake`) |
| `model` | 모델 이름 |
| `createdAt` | 메모를 남긴 시각 |

주문 서비스 환경변수 `ORDER_AI_ENABLED`(기본 false) · `ORDER_AI_API_KEY`(없으면 꺼짐) · `ORDER_AI_PROVIDER`(`openai-compatible` · `fake`) · `ORDER_AI_BASE_URL` · `ORDER_AI_MODEL`(기본 Groq). AI에는 개인정보를 보내지 않는다.

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
| POST | `/api/v1/dev/wallets/me/charges` | 개발용 예치금 충전(토스 없이 내 지갑에 입금, 원장 `DEV_CHARGE`). local · test · dev 프로필에서만 | 🔒 |
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
| GET | `/internal/sellers/{sellerId}/withdrawal-blockers` | 판매자 탈퇴를 막는 정산 사유. `blockers`에 `UNSETTLED_ITEM`(정산서에 아직 안 들어간 구매확정 항목) · `UNPAID_SETTLEMENT`(지급 대기 `COMPLETED` · 지급 실패 `FAILED` 정산서)와 건수(`count`)를 이 순서로, 0건인 사유는 빼고 준다. 없으면 `[]`(모르는 판매자도 200) | 내부 |

## 커뮤니티

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/reviews/eligible` | 리뷰를 쓸 수 있는 주문상품(커서 목록) | 🔒 |
| POST | `/api/v1/reviews/images/upload-urls` | 리뷰 사진 업로드 주소(Presigned PUT) 발급. 본문 `files`(`contentType` `image/jpeg` · `image/png` · `image/webp`, `contentLength` 1장 10MB 이하) 1~5장. 응답은 `files` 배열(요청 순서대로 `uploadUrl` · `imageUrl` · `expiresAt`(5분 뒤 만료)) | 🔒 |
| POST | `/api/v1/reviews` | 리뷰 작성(201 + `Location`). `imageUrls`는 업로드 주소 발급으로 받은 내 `imageUrl` 목록(최대 5장, 빼거나 `null`이면 사진 없음) | 🔒 |
| PATCH | `/api/v1/reviews/{reviewId}` | 리뷰 수정(보낸 필드만, 작성자만). `imageUrls`를 보내면 목록 전체 교체, 빈 목록은 사진 모두 삭제, 안 보내면 그대로(`null`은 400) | 🔒 |
| DELETE | `/api/v1/reviews/{reviewId}` | 리뷰 삭제(작성자만). 리뷰에 붙은 사진 목록도 함께 지워진다 | 🔒 |
| GET | `/api/v1/reviews` | 상품별 리뷰 목록(`productId`, 최신순). 공개 리뷰만 | 🌐 |
| GET | `/api/v1/reviews/me` | 내 리뷰(숨겨진 리뷰 포함) | 🔒 |
| GET | `/api/v1/reviews/summary` | 리뷰 요약(리뷰 수 · 평균 별점 · 사이즈 핏 분포 `fitSummary`). 공개 리뷰만 센다 | 🌐 |
| PUT | `/api/v1/favorites/{productId}` | 찜(다시 해도 1건) | 🔒 |
| DELETE | `/api/v1/favorites/{productId}` | 찜 취소(없어도 204) | 🔒 |
| GET | `/api/v1/favorites` | 내 찜 목록. `productIds`를 주면 그중 찜한 것만 | 🔒 |
| GET | `/api/v1/favorites/summary` | 찜 요약(찜 수) | 🌐 |
| GET | `/api/v1/admin/community/reviews` | 검사 상태별 리뷰 목록(`status` 필수: `PENDING` · `PASSED` · `HIDDEN` · `CONFIRMED_HIDDEN` · `RESTORED`, `cursor` · `size`, 최신순) | 🛠 |
| PATCH | `/api/v1/admin/community/reviews/{reviewId}/visibility` | 숨김 확정 · 다시 공개(본문 `visibility` `HIDDEN` → `CONFIRMED_HIDDEN`, `VISIBLE` → `RESTORED`). 같은 값을 다시 보내도 200 | 🛠 |
| GET | `/api/v1/admin/community/dead-letters` | DLT에 남은 메시지 목록(`status` 기본 OPEN, `page` · `size` 기본 0 · 20, 1~100) | 🛠 |
| GET | `/api/v1/admin/community/dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/community/dead-letters/{id}/reprocess` | DLT 메시지 재처리 | 🛠 |
| POST | `/api/v1/admin/community/mcp` | 커뮤니티 운영 조회 MCP 창구(읽기 전용, 스위치 기본 꺼짐) | 🛠 |

리뷰 목록(상품별 · 내 리뷰) 항목에는 사진 공개 주소 `imageUrls`(순서대로, 없으면 빈 목록)와 `visibility`가 있다. `visibility`는 `PUBLIC`(공개) 또는 `UNDER_REVIEW`(숨겨짐: 검사 숨김 · 숨김 확정)이고, `UNDER_REVIEW`는 내 리뷰에서만 보인다. 상품별 목록은 늘 `PUBLIC`이다. 커서 목록의 `size`는 1~50(기본 20)이다.

**리뷰 사진**: 업로드 주소 발급으로 받은 `uploadUrl`에 요청한 `Content-Type` · 크기 그대로 PUT 해 S3에 직접 올리고, 받은 `imageUrl`을 작성 · 수정의 `imageUrls`에 넣는다. 본인에게 발급된 주소만 받는다. 리뷰에 새로 붙는 사진은 실제로 올라갔는지 확인한다(이미 붙어 있던 사진은 다시 확인하지 않는다). 올라가지 않았으면 400 `IMAGE_NOT_UPLOADED`, 저장소가 답하지 못하면(장애 · 시간 초과 · 권한) 503 `IMAGE_STORAGE_UNAVAILABLE`이고 요청은 반영되지 않는다. 최대 장수 · 크기 · 만료는 `kidly.community.review.image.*`(`max-count` · `max-file-size` · `upload-url-expiry`), 확인 방식은 `upload-check`(`s3` 기본, 로컬 실행은 `none`)다.

**리뷰 검사(#1002)**: 리뷰는 저장하면 바로 공개되고 검사 대기(`PENDING`)가 된다. 스케줄러가 1분 간격으로 대기 리뷰를 10건씩 금칙어 → AI(키가 있을 때만) 순으로 검사해, 통과하면 `PASSED`, 걸리면 `HIDDEN`(숨김, 관리자 판단 대기)으로 바꾼다. 숨겨진 리뷰는 상품별 목록 · 리뷰 요약에서 빠지고 작성자의 내 리뷰에는 `UNDER_REVIEW`로 보인다. 관리자는 `CONFIRMED_HIDDEN`(숨김 확정) 또는 `RESTORED`(다시 공개)로 정한다. 공개 상태(`PENDING` · `PASSED` · `RESTORED`)에서 글 · 사진을 고치면 다시 검사 대기가 되고, 숨김 상태(`HIDDEN` · `CONFIRMED_HIDDEN`)는 작성자가 고쳐도 숨김 그대로다. 판단하지 못한 검사(장애 · 시간 초과)는 다시 하고, 판단하지 못한 횟수가 10번(`max-attempts`)에 닿으면 공개 그대로 관리자 목록에서 본다(`attemptsExhausted`). AI 한도 초과(429)는 횟수에 넣지 않고 그 주기의 남은 검사만 멈춘다.

**커뮤니티 운영 조회 MCP 창구(#1005)**: AI가 커뮤니티 운영 상태(리뷰 요약 · DLT · 검사 현황 · 상품 복사본)를 조회해 원인을 진단하는 읽기 전용 [MCP](https://modelcontextprotocol.io) 서버다. 재처리 · 숨김 같은 조작 도구는 없고, 조작은 위 관리자 API로 사람이 한다.

- 켜기: 커뮤니티 서비스 환경변수 `COMMUNITY_MCP_ENABLED=true`(설정 `kidly.community.mcp.enabled`, 기본 false). 꺼져 있으면 경로가 없다. 목록 한 번의 줄 수 상한은 `kidly.community.mcp.max-results`(기본 50, 1~100)
- 연결: 상태 없는 Streamable HTTP(세션 없음). `POST /api/v1/admin/community/mcp`에 JSON-RPC 본문, `Content-Type: application/json`, `Accept: application/json, text/event-stream`. 관리자 토큰으로 Gateway를 거친다. `X-Admin-Id`가 없거나 양의 정수가 아니면 401, 본문이 64KB를 넘으면 413 ProblemDetail
- 결과: 도구마다 구조화 결과(`structuredContent`)와 같은 내용의 JSON 텍스트 하나. 목록 `size`가 상한보다 크면 상한으로 줄인다. 시각은 `yyyy-MM-ddTHH:mm:ss`(한국 시간). 없는 대상 · 잘못된 인자 · 모르는 도구는 `isError: true`와 정해진 문구(받은 값 · 예외 메시지는 넣지 않는다)
- 응답에 넣지 않는 것: 리뷰 본문 · 닉네임 · 회원 ID · 사진 주소 · DLT 원본 본문 · 예외 메시지 · 스택 · 처리한 관리자. 자유 문구가 될 수 있는 칸은 정해진 형식일 때만 내보내고 아니면 `null`이다:
  - 토픽 · `eventType`: 소문자 토픽 이름 · 점으로 나뉜 소문자 이벤트 타입만
  - 실패 종류(`failureType` · `lastReplayFailureType`): 예외 클래스의 단순 이름만
  - 메시지 키 · `eventId`: 숫자 ID 또는 UUID만

| 도구 | 인자 | 결과 |
|---|---|---|
| `review_summary` | `productId`(필수) | 상품 하나의 `reviewCount` · `averageRating` · `fitSummary`(공개 리뷰 기준, 리뷰 요약 API와 같다) |
| `dead_letters` | `state`(`OPEN` · `REPROCESSED`, 기본 OPEN) · `page` · `size`(기본 20) | DLT 목록 `{state, page, size, totalElements, items}`. 항목은 ID · 원래 토픽 · `eventType` · 상태 · 재처리 횟수 · 실패 종류 · 시각 |
| `dead_letter_detail` | `id`(필수) | DLT 한 건의 DLT · 원래 토픽 위치(파티션 · 오프셋) · 메시지 키 · `eventType` · `eventId` · 실패 종류 · 상태 · 재처리 결과 |
| `moderation_queue` | 없음 | 상태별 리뷰 수(`PENDING` · 재시도 상한에 닿은 `PENDING_EXHAUSTED` · `PASSED` · `HIDDEN` · `CONFIRMED_HIDDEN` · `RESTORED`), 가장 오래된 대기 리뷰의 작성 시각, 최근 숨긴 리뷰의 ID · 이유 분류 · 시각(최대 상한 건수) |
| `replica_health` | 없음 | 상품 복사본 수 · 표시 상태별 수 · 마지막 반영 시각 · 가장 늦은 원본 이벤트 시각 |

오류 코드(상태 · `title` · `detail`은 `ReviewErrorCode`에 있다):

- 사진: `INVALID_IMAGE_FILE` · `TOO_MANY_IMAGES` · `INVALID_IMAGE_URL` · `IMAGE_NOT_UPLOADED`(400), `IMAGE_STORAGE_UNAVAILABLE`(503)
- 검사: `INVALID_MODERATION_STATUS` · `INVALID_VISIBILITY`(400)
- MCP: `MCP_REQUEST_TOO_LARGE`(413)

## 알림

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/v1/notifications` | 내 알림함(최신순, `page` · `size` 1~100, 기본 0 · 20) | 🔒 |
| GET | `/api/v1/notifications/unread-count` | 안 읽은 내 알림 개수 `{ count }` | 🔒 |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 읽음 처리(204, 다시 해도 204). 없거나 남의 알림은 404 | 🔒 |
| PATCH | `/api/v1/notifications/read-all` | 내 안 읽은 알림 모두 읽음 처리(204, 없어도 204). 이미 읽은 알림의 읽은 시각은 그대로 | 🔒 |
| GET | `/api/v1/child/notifications` | 자녀 계정이 자기 알림함 조회. 응답 · 규칙은 회원 알림함과 같다 | 🔒 |
| GET | `/api/v1/child/notifications/unread-count` | 자녀 계정의 안 읽은 알림 개수 `{ count }` | 🔒 |
| PATCH | `/api/v1/child/notifications/{notificationId}/read` | 자녀 계정의 알림 읽음 처리(204). 없거나 다른 주체의 알림은 404 | 🔒 |
| PATCH | `/api/v1/child/notifications/read-all` | 자녀 계정의 안 읽은 알림 모두 읽음 처리(204) | 🔒 |
| PUT | `/api/v1/notifications/push-tokens` | 이 브라우저의 웹 푸시 FID 등록. 본문 `{ fid }`(1~1024자). 204, 다시 해도 204. 다른 회원이 쓰던 FID면 주인을 바꾼다 | 🔒 |
| DELETE | `/api/v1/notifications/push-tokens` | 로그아웃 때 이 브라우저의 FID 해제. 본문 `{ fid }`. 204, 없거나 남의 FID여도 204(남의 것은 지우지 않는다) | 🔒 |
| GET | `/api/v1/admin/notification-dead-letters` | DLT에 남은 메시지 목록(기본 OPEN) | 🛠 |
| GET | `/api/v1/admin/notification-dead-letters/{id}` | DLT 메시지 상세 | 🛠 |
| POST | `/api/v1/admin/notification-dead-letters/{id}/reprocess` | DLT 메시지 재처리(원래 토픽에 다시 넣지 않음. 탈퇴한 회원의 가입 이벤트는 반영하지 않는다) | 🛠 |

알림함 알림은 만든 지 90일이 지나면 읽음 여부와 상관없이 지워진다(매일 04:00 KST).
알림은 받는 사람을 로그인 주체(회원 · 자녀 계정)의 종류와 ID로 저장한다. `/api/v1/notifications`는 회원 토큰, `/api/v1/child/notifications`는 자녀 토큰으로만 부를 수 있고, 같은 숫자 ID라도 다른 주체의 알림은 보이지 않는다. 자녀 알림함에는 조르기 거절(`WISH_REJECTED`)이 들어오고, 주문이 이벤트를 보내기 시작하면(#1018) 조르기 결제 완료(`WISH_PAID`) · 만료(`WISH_EXPIRED`)도 들어온다. 셋 다 `linkPath`는 `/child/wishes/{wishRequestId}`이며, 자녀 계정을 지우면 그 계정의 알림도 지워진다.

웹 푸시는 FCM으로 보낸다. 대상은 등록 토큰이 아니라 FID(Firebase Installation ID)다. 프론트는 Firebase Messaging의 `register(messaging, { vapidKey })`와 `onRegistered`로 받은 FID를 사이트에 들어올 때와 `onRegistered`가 불릴 때마다 `PUT`하고, 로그아웃 때 `DELETE`한다(서비스 워커 `firebase-messaging-sw.js` 필요).
푸시로 보내는 알림은 회원이 사이트를 떠난 뒤 일어나는 일이다: 주문 확인(`ORDER_PAID`), 구매자가 하지 않은 주문 취소(`ORDER_CANCELLED` · `ORDER_ITEM_CANCELLED`), 배송 출발 · 완료(`DELIVERY_SHIPPED` · `DELIVERY_DELIVERED`), 판매자 새 주문(`SELLER_ORDER_PAID`). 제목 · 본문은 알림함 알림과 같고, 누르면 알림함 `linkPath`(없으면 사이트 첫 화면)를 연다. 자녀 계정에는 보내지 않는다.
회원 한 명의 FID는 10개까지 두고(넘으면 마지막 등록이 오래된 것부터 지운다), 270일 동안 다시 등록되지 않은 FID와 FCM이 무효라고 답한 FID는 지운다(매일 04:10 KST). 탈퇴하면 그 회원의 FID를 모두 지운다.
