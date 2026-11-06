# 도메인별 만들 것 — API · 이벤트

**지금 있는 API는 [API 목록](../api/README.md)에 도메인별로 있습니다.** 아래 표에는 아직 만들지 않았거나 만들지 않기로 한 API와 이벤트만 남깁니다 (앞의 `/api/v1` 생략). `단계` 열: ①~⑦ = 정상 흐름에 필요한 카드 · **보완** = 정상 흐름 다음 · **최종** = 최종 프로젝트(완료 · 진행 중 · 예정을 설명에 적음) · "만들지 않음" · "세미 제외" · "최종" 줄을 빼면 이 장의 API는 모두 구현되어 있습니다

v2.0: 이벤트는 모두 Kafka로 갑니다. 토픽은 `kidly.{보내는 도메인}.events`이고(3.3), 받는 쪽은 구독 그룹 `kidly.{받는 도메인}`으로 받습니다.

### 이벤트 한눈에 보기

| 이벤트 | 보내는 쪽 → 받는 쪽 | 받는 쪽이 하는 일 | `data` 핵심 필드 | 단계 |
|---|---|---|---|---|
| `member.registered` 회원가입됨 | 회원 → 결제 · 알림 | 지갑 생성 · 환영 메일 | memberId · email | ① |
| `member.changed` 회원 정보 변경됨 | 회원 → 판매자 | 신청자 닉네임 반영 | memberId · nickname | 보완 |
| `member.withdrawn` 회원탈퇴됨 | 회원 → 회원(배송지) · 자녀 프로필 · **결제** (추천 · 커뮤니티 예정) | 배송지 · 자녀 프로필 삭제 · **지갑 동결** / 활동 기록 · AI 응답 삭제(추천, P16) · 찜 삭제 · 리뷰 작성자 표시(커뮤니티, #663) | memberId | 보완 · 최종 |
| `child.changed` 자녀 정보 변경됨 | 자녀 프로필 → 추천 · 주문 | 복사본 저장 (삭제면 삭제 직전 값 + deleted) | childId · memberId · heightCm · weightKg · gender · birthDate · bodyType(없으면 null) · preferredFit(없으면 null) · deleted | ① |
| `seller.changed` 판매자 정보 변경됨 | 판매자 → 회원 · 상품 · 주문 · 추천 · **검색** | 복사본 저장 (승인 · 정지 때 발행) · 상호명 색인(검색) | sellerId · businessName · status(ACTIVE · SUSPENDED) | ② · 최종 |
| `product.changed` 상품 정보 변경됨 | 상품 → 추천 · 주문 · **검색 · 커뮤니티** · 상품(검색용 표, 되돌리기용) | 복사본 저장 (판매 상태 변경 · 수정 · 품절 변경 때 발행) · 색인(검색) · 임베딩 다시 생성(추천) | productId · sellerId · name · categoryId · status · thumbnailUrl · createdAt · sizeFree · infoValues · options(optionId · optionName · size · price · minHeightCm · maxHeightCm · soldOut · saleStatus · values) · **brand · description · sizeSpecs**(#610) · **categoryPath**(id · name, 대분류 → 종류, #955) | ② · 최종 |
| `order.created` 주문 생성됨 | 주문 → 상품 | 재고 차감 (선점) | orderId · memberId · items(orderItemId · optionId · quantity) | ④ |
| `stock.deducted` 재고 차감됨 | 상품 → 주문 | 결제 요청 (결제대기일 때만) | orderId | ④ |
| `order.payment-requested` 결제 요청됨 | 주문 → 결제 | 예치금으로 결제 | orderId · memberId · amount · items(orderItemId · amount) | ④ |
| `payment.completed` 결제 완료됨 | 결제 → 주문 | 주문 PAID · 판매자별 배송 생성 | paymentId · orderId · amount · memberId · items(orderItemId · amount) · paidAt | ④ |
| `order-item.purchase-confirmed` 구매확정됨 | 주문 → 정산 · 추천 · **커뮤니티** | 정산 대상 등록 / 구매 기록 저장 (자녀 · 사이즈가 있을 때만) / 리뷰 작성 자격 | orderItemId · orderId · memberId · sellerId · childId · productId · optionId · size(사이즈 없는 상품은 null) · quantity · amount(단가 × 수량) · confirmedAt | ⑥ · 최종 |
| `settlement.payout-requested` 정산 지급 요청됨 | 정산 → 결제 | 판매자 지갑 입금 (계산 완료 = 지급 요청) | settlementId · sellerId · amount · periodStartOn · periodEndOn | ⑦ |
| `payment.payout-completed` 지급 완료됨 | 결제 → 정산 | 정산서 PAID | settlementId · sellerId · amount · ledgerId(0원이면 null) · paidAt | ⑦ |
| `payment.payout-failed` 지급 실패함 | 결제 → 정산 | 정산서 FAILED | settlementId · sellerId · amount · reasonCode · failedAt | 보완 |
| `stock.deduction-failed` 재고 부족함 | 상품 → 주문 | 주문 취소 | orderId · reason · failures(orderItemId · reason) — reason: OUT_OF_STOCK · PRODUCT_NOT_ON_SALE · OPTION_NOT_ON_SALE | 보완 |
| `payment.failed` 결제 실패됨 | 결제 → 주문 | 주문 취소 | paymentId(금액 불일치면 null) · orderId · memberId · requestedAmount · reasonCode(INSUFFICIENT_BALANCE · AMOUNT_MISMATCH) · failedAt | 보완 |
| `order.cancelled` 주문 취소됨 | 주문 → 상품 · 결제 (추천 예정) | 재고 복구 / 결제됐으면 남은 금액 환불, 아직이면 이후 결제 요청 거절 / 구매자 취소면 활동 기록(추천) | orderId · memberId · reason(OUT_OF_STOCK · PAYMENT_FAILED · PAYMENT_EXPIRED · BUYER_CANCELLED) · items(orderItemId · optionId · quantity) | 보완 |
| order-item.cancelled 판매자 주문상품 취소됨 | 주문 → 결제 · 상품 | 주문상품 금액만큼 환불 / 재고 복구 | orderId · memberId · sellerId · cancellationId · reason(OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER) · items(orderItemId · optionId · quantity · amount) | 최종 |
| payment.refunded 환불 완료됨 (계약만) | 결제 → 주문 · 정산 | 취소 완료 / 미정산 대상 제외 (K18) | refundId · paymentId · orderId · cancellationId · items(orderItemId · amount) · amount · refundedAt | 최종 |
| cart.item-added 장바구니 담김(발행은 이미 있음) | 주문 → 추천 | 활동 기록 저장, 선호 점수 갱신 | cartItemId · memberId · childId · productId · optionId · quantity · cartQuantity | 최종 |
| cart.item-removed 장바구니 빠짐(발행 요청) | 주문 → 추천 | 활동 기록 저장, 선호 점수 갱신 | cartItemId · memberId · childId · productId · optionId · quantity · cartQuantity | 최종 |
| search.performed 검색어 기록됨(발행은 이미 있음) | 검색 → 추천 | 활동 기록 저장 | memberId · keyword · categoryId · attributeValueIds · resultCount | 최종 |
| child.reference-garment.changed 기준 옷 변경됨 | 자녀 프로필 → 추천 | 기준 옷 복사본 저장 | childId · part(TOP · BOTTOM) · lengthCm · widthCm · sourceOrderItemId | 최종(2순위) |
| `member.tokens-revoked` 토큰 무효화됨 | 회원 → API Gateway | 무효화 목록에 넣어 그 회원의 기존 토큰 거절 (도메인 아님) | memberId · 무효화 시각 | 최종 |
| `wish.item-added` · `wish.sent` · `wish.approved` · `wish.rejected` 조르기 | 주문 → (추천 · 알림 예정) | 활동 신호 · 인앱 알림 | 키 = 자녀 프로필 ID | 최종(예정) |
| `review.created` 리뷰 작성됨 | 커뮤니티 → (미정) | 추천 · 검색 신호 (K28) | 리뷰 ID · 상품 ID · 주문상품 ID · 회원 ID · 별점 · 작성 시각(본문 · 닉네임 제외) | 최종(보류) |

이벤트 형식 · 보내고 받는 법은 6.2.

## 회원 · maruhxn

코드는 member(회원 · 배송지 · 인증) · childprofile(자녀 프로필) · seller(판매자) · notification(알림) 모듈로 나뉩니다.

| 단계 | API | 설명 |
|---|---|---|
| 보완 | `POST /admin/members/{memberId}/grant-admin` · `/revoke-admin` | 관리자 권한 부여 · 회수 |
| 만들지 않음 | `PUT /seller/payout-account` | 정산계좌 등록 · 변경 — 정산계좌 없음 (4장) |

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | member.registered (①) · member.changed (보완) · member.withdrawn (보완) · child.changed (①, 자녀 프로필) · seller.changed (②, 판매자) |
| 받는 이벤트 | member.withdrawn → 배송지 · 자녀 프로필 삭제 · seller.changed → 판매자 상태 복사본(토큰 역할 계산) · member.changed → 신청자 닉네임 · member.registered → 환영 메일(알림) |
| 시간 작업 | 만료된 Refresh Token 정리 · 실패한 알림 메일 재발송 (1분마다) |

자녀 API(등록 · 목록 · 상세 · 수정 · 삭제)는 회원 담당입니다. 명세 v2의 추천 도메인 표시와 다르며, 이 문서를 따릅니다.

최종: 보내는 이벤트에 `member.tokens-revoked`(→ Gateway)가 더해졌고, 받는 이벤트는 모두 Kafka입니다. 인증은 auth 도메인으로 분리 중이며(#686, PR #717), 머지되면 무효화 이벤트가 `auth.tokens-revoked`(`kidly.auth.events`)로 바뀝니다. 기준 옷 API(2순위)는 ① 카드에 있습니다.

## 상품 · heojungseok

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | product.changed (②) · stock.deducted (④) · stock.deduction-failed (보완) |
| 받는 이벤트 | order.created 재고 차감 (선점) (④) · order.cancelled 재고 복구 · 이후 차감 거절 (보완) · seller.changed 판매자 복사본 · product.changed 검색용 표 갱신 (되돌리기용) |
| 최종 | 판매자 · 주문 이벤트를 Kafka로 받음(그룹 `kidly.product`, #692) · `order-item.cancelled` 재고 복구(#716) · `product.changed`에 brand · description · sizeSpecs 추가(#610) · `product.changed`에 categoryPath 칸 추가(#955) · 보내는 이벤트는 아웃박스 |

## 검색 · codes-gy (새 표)

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | search.performed (최종, `kidly.search.events`, 키 회원 ID) |
| 받는 이벤트 | product.changed 상품 문서 색인 · seller.changed 상호명 색인 (최종) |
| 구독 그룹 · DLT | `kidly.search`. `kidly.product.events.search.dlt` · `kidly.seller.events.search.dlt`. 읽기 시작 위치는 latest라 기존 상품은 처음 채우기로 넣음 |
| 실행 | apps/search-service. dev만 켜짐: 검색 서비스는 t3.large compose(프로필 `search`), Elasticsearch는 t3.medium k3s(3.1). 모니터링은 앱과 나눈 job `kidly-search` · 로그 `service_name=kidly-search` |

## 추천 · heojungseok

| 단계 | API | 설명 |
|---|---|---|
| 최종 | `GET /recommendations/companions?productId=&childId=` | 함께 사면 좋은 상품 4개(카테고리 짝 규칙 → 벡터 후보 20개 → AI 선택) |
| 최종 | `POST /recommendations/product-views` | 상품 상세 진입 기록 |
| 최종 | `/missions` · `/purchase-requests` … | 미션 쇼핑(v1.1 그대로, Kids Mode에서 정함) |

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | 없음 |
| 받는 이벤트 | child.changed · product.changed(보강 칸 포함) 복사본 저장과 임베딩 갱신 · seller.changed 판매자 복사본 · order-item.purchase-confirmed 구매 기록(자녀 · 사이즈가 있을 때만) · cart.item-added · cart.item-removed(발행 요청) · order.cancelled(reason이 `BUYER_CANCELLED`일 때만) 활동 기록과 선호 점수 갱신 · search.performed 활동 기록 · member.withdrawn 활동 기록 · AI 응답 삭제 · child.reference-garment.changed 기준 옷 복사본(2순위) |
| 구독 그룹 · DLT | `kidly.recommend`, 토픽마다 `{토픽}.recommend.dlt`, `occurredAt` 비교로 늦게 온 옛 이벤트 무시 |

## 주문 · L2J92

| 단계 | API | 설명 |
|---|---|---|
| 만들지 않음 | `POST /orders/{orderId}/items/{orderItemId}/cancel` | 구매자 주문상품 취소 (구매자 부분 취소 없음. 판매자 취소는 아래) |

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | order.created (④) · order.payment-requested (④) · order-item.purchase-confirmed (⑥) · order.cancelled (보완, 구매자 취소 포함) |
| 받는 이벤트 | child.changed · product.changed 복사본 저장 (④) · stock.deducted (④) · payment.completed (④) · stock.deduction-failed · payment.failed · seller.changed 판매자 복사본 (보완) |
| 시간 작업 | 자동 구매확정 (⑥, 10분마다) · 결제 제한 시간 만료 (보완, 1분마다) |
| 보내는 이벤트 (최종) | `cart.item-added` · `order-item.cancelled`(스위치) — 모두 아웃박스로 `kidly.order.events`. 예정: `cart.item-removed` · `wish.item-added` · `wish.sent` · `wish.approved` · `wish.rejected` |
| 받는 이벤트 (최종) | 상품 · 판매자 · 자녀 프로필 · 결제 토픽을 모두 Kafka로(그룹 `kidly.order`). 실패는 `{토픽}.order.dlt`, 관리자가 DLT를 확인 · 재처리 · 종료 |
| 시간 작업 (최종) | 택배 조회 자동 배송 완료 · 아웃박스 릴레이. 주기 작업은 한 서버에서만(advisory lock) |
| 외부 연동 (최종) | Delivery Tracker (인증값이 없으면 기능만 꺼짐). 서킷브레이커(Resilience4j)는 예정(#708) |

## 결제 · codes-gy

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | payment.completed (④) · payment.failed (보완) · payment.payout-completed (⑦) · payment.payout-failed (보완) — 모두 아웃박스로 `kidly.payment.events`에. payment.refunded는 계약만 있음(K18) |
| 받는 이벤트 | member.registered 지갑 생성 (①) · **member.withdrawn 지갑 동결 (최종)** · order.payment-requested 예치금 결제 (④) · order.cancelled 환불 · 이후 결제 요청 거절 (보완) · settlement.payout-requested 판매자 입금 (⑦) · **order-item.cancelled 부분 환불 (최종, #711 · Kafka만)** |
| 구독 그룹 · DLT | `kidly.payment`. `kidly.member.events.payment.dlt` · `kidly.order.events.payment.dlt` · `kidly.settlement.events.payment.dlt`. 받는 종류는 헤더 eventType으로 고르고 그 밖의 종류는 건너뜀 |
| 시간 작업 | 충전 정리 (1분마다) · 주문 취소 환불 재처리 (1분마다) · **아웃박스 릴레이 (1초마다)** · **보낸 아웃박스 정리 (매일 4시)** |
| 외부 연동 | 토스페이먼츠 (충전 승인 · 취소 · 조회 · 웹훅). 연결 3초 · 읽기 30초 제한 |

## 정산 · baiees

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | `settlement.payout-requested` (⑦) |
| 받는 이벤트 | `order-item.purchase-confirmed` 정산 대상 등록 (⑥) · `payment.payout-completed` · `payment.payout-failed` 지급 결과 반영 (⑦) |
| 시간 작업 | 영업일 일정산 — Spring Batch `settlementJob`, 영업일 09:05~17:05 매시간 시도 · 하루 1회 (⑦) |

## 공통 · 팀

| 단계 | API | 설명 |
|---|---|---|
| 운영 | `/actuator/health` · `/actuator/prometheus` | 상태 확인 · 메트릭 수집 (밖에서는 Nginx가 막음) |
| 최종 | `apps/gateway` | 서비스 API가 아님. 경로별 라우팅 · 토큰 검증 · 역할 검사 · CORS · 보안 응답 헤더, 정규화되지 않은 경로는 400 |

## 커뮤니티 · L2J92

세미의 「리뷰 · 최종 프로젝트」를 대신합니다. 커뮤니티는 회원이 상품에 남기는 반응을 저장하고 보여 줍니다. 모듈 `domains/community`(컨텍스트 review · favorite), DB 스키마 `community`.

| ID | 기능 | 이슈 | 상태(11/2) |
|---|---|---|---|
| CM-01 | 커뮤니티 모듈 · 스키마 기본 구성 | #657 | 머지 |
| CM-02 | 상품 복사본 · 판매 상태 표시 | #658 | 머지 |
| CM-03 | 리뷰 작성 자격(구매확정 수신) · 작성 가능 목록 | #659 | 머지 |
| CM-04 | 리뷰 작성 · 수정 · 삭제, 상품별 집계 | #660 | 머지 |
| CM-05 | 상품별 리뷰 목록 · 내 리뷰 · 리뷰 요약 | #661 | 머지 |
| CM-06 | 찜 추가 · 취소 · 내 찜 목록 · 찜 수 | #662 | 진행 중 |
| CM-07 | 회원 탈퇴 반영 | #663 | 예정 |

| 맡지 않는 일 | 담당 도메인 |
|---|---|
| 구매확정 판단 | 주문 |
| 상품 원본 정보 · 상세 설명 · 이미지 | 상품 |
| 알림 발송 | 알림 |
| 리뷰 · 찜을 추천 신호로 쓸지 | 추천 |
| 검색 정렬(별점순 등) | 검색 |
| FAQ 자동 응답(챗봇) | 미정 (별도 모듈 검토) |

| 구분 | 내용 |
|---|---|
| 보내는 이벤트 | 없음. `review.created`(`kidly.community.events`)는 받을 도메인이 정해지면 아웃박스로 (K28) |
| 받는 이벤트 | `product.changed` 상품 복사본(더 늦게 발생한 것만) · `order-item.purchase-confirmed` 리뷰 작성 자격(주문상품마다 1건) · `member.withdrawn` 찜 삭제 · 리뷰 작성자 표시(예정) |
| 구독 그룹 · DLT | `kidly.community`, 실패는 1초 간격 3번 뒤 `{토픽}.community.dlt`(관리자 재처리 화면 없음). 수신 스위치 `kidly.community.kafka.consumer.enabled`(기본 켬) |
