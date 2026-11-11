# 용어

| 한글 | 코드 이름 (안) | 뜻 |
|---|---|---|
| 자녀 프로필 | `ChildProfile` | 보호자 계정 안의 자녀 정보 |
| 성장 이력 | `GrowthRecord` | 자녀의 키 · 체형이 바뀔 때마다 쌓는 기록 |
| 배송지 | `ShippingAddress` | 회원이 등록한 주소 · 수령인, 배송지가 있으면 기본 배송지는 1개 |
| 판매자 | `Seller` | 승인된 판매자 정보 (상호명 · 상태), 판매자 ID = 회원 ID |
| 옵션 | `ProductOption` | 옵션 항목 값(예: 색상 × 사이즈) 한 조합, 재고 단위 |
| 재고 차감 (선점) | `deduct` | 주문 저장 때 판매 가능 수량을 바로 줄임 (나중에 확정하는 단계 없음), 주문 취소 때 복구 |
| 결제대기 | `PENDING_PAYMENT` | 주문 저장 직후 상태 |
| 판매자별 주문 | `SellerOrder` (코드: `Delivery`, API는 sellerOrderId) | 한 주문을 판매자별로 나눈 것, 배송 단위 |
| 주문상품 | `OrderItem` | 주문 안의 상품 한 줄, 취소 · 구매확정 · 정산 단위 |
| 구매확정 | `confirm` | 이후 취소 · 환불 없음, 정산 대상이 됨 |
| 예치금 · 지갑 | `Wallet` | 충전해 두고 결제에 쓰는 돈 |
| 정산 대상 | `SettlementItem` (상태 PENDING · SETTLED · EXCLUDED) | 구매확정된 주문상품 1건. 정산서에 들어가면 그 정산서의 정산 항목 |
| 정산서 | Settlement | 판매자 · 정산 실행(종료일)별 정산 결과 (수수료 · 지급액 · 상태 COMPLETED = 지급 요청 · PAID · FAILED) |
| 결제 복구 | `PaymentRecovery` | 결제 완료를 주문에 반영하지 못한 건. 관리자가 재처리 · 취소하는 처리 대기 목록 |
| 이벤트 처리 실패 | `OrderEventFailure` | 주문이 받은 이벤트(재고 차감됨 · 재고 부족함 · 결제 실패됨) 처리에 실패한 건. 처리 대기 목록 |
| 상태 이력 | `OrderStatusHistory` | 주문 · 주문상품 · 배송 · 결제 복구의 상태 변경 기록 (누가 · 언제) |
| 멈춘 주문 | `StuckOrderKind` | 기준 시간을 넘겨 다음 단계로 가지 않은 주문의 종류 (조회만) |
| 정산 실행 | settlementJob | 정산 작업 1회 (자동 · 수동). 기록은 Spring Batch 메타데이터, 응답 상태 RUNNING · COMPLETED · PARTIAL_FAILED · FAILED |
| 알림 | `Notification` | 보낼 메일 한 건 (지금은 가입 환영 메일만) |
| 상품 유형 | `ProductType` | 상품 등록 입력 양식 (의류 · 모자 · 양말). 말단 카테고리마다 하나 |
| 항목 | `Attribute` | 상품 유형이 쓰는 입력 칸. 옵션 항목(색상 · 사이즈)과 정보 항목(주요소재 · 혼용률) |
| 항목 값 | `AttributeValue` | 항목에서 고르는 값 (예: 네이비, 110) |
| 묶음 · 말단 카테고리 | `Category` | 묶음 카테고리(의류 · 잡화) 아래 말단 카테고리(상의 · 하의 등). 상품은 말단에만 등록 |
| 사이즈 역할 | `SIZE` | 사이즈 항목. 권장 키 · 실측을 사이즈 값마다 받음 |
| 사이즈 무관 | `sizeFree` | 사이즈 구분이 없는 상품 (추천은 SIZE_FREE 안내) |
| 옵션 이름 | `optionName` | 옵션 항목 값을 이은 이름 (예: 색상: 네이비 / 사이즈: 110) |
| 실측 항목 | `MeasurementItem` | 총장 · 어깨너비 등 사이즈별로 재는 칸, 카테고리가 필수 항목을 정함 |
| 차감 기록 | `InventoryDeduction` | 주문상품마다 재고 차감 결과 (DEDUCTED · RESTORED · FAILED) |
| 재고 조정 기록 | `InventoryAdjustmentRecord` | 판매자 재고 조정 1건 (고치지 않음) |
| 검색용 표 | ProductSearchDocument | 상품의 되돌리기용 검색 표 (목록 검색은 검색 서비스로 옮김) |
| 적합도 | `Fit` | ROOMY(여유 있음) · PROPER(적정) · TIGHT(작을 가능성 있음) |
| 안내 코드 | `SizeNotice` | SIZE_FREE · NO_MATCHING_SIZE · BODY_TYPE_ADJUSTED · PREFERRED_FIT_NOT_APPLIED · HEIGHT_ONLY |
| 후보 사이즈 | `candidateSizes` | 추천 규칙의 후보가 된 사이즈 목록 (AI가 고른 사이즈는 이 목록 밖의 상품 사이즈일 수 있음) |
| 근거 종류 | `SizeReasonType` | 사이즈 추천 근거의 종류. HEIGHT · BODY_TYPE · PREFERRED_FIT · MEASUREMENT · RULE_RESULT · GROWTH |
| 추천 출처 | `source` | 사이즈 추천 답을 누가 골랐는지. AI · RULE(규칙) |
| 지급 요청 | payout | 정산서 금액을 판매자 지갑에 적립해 달라는 요청, 결제가 처리 |
| 영업일 일정산 | - | 주말 · 공휴일을 뺀 날마다 하루 한 번 정산. 공휴일은 특일 정보 API로 판단 |
| 원장 | WalletLedger | 지갑 잔액이 바뀔 때마다 남기는 기록. 종류 CHARGE · CHARGE_CANCEL · CHARGE_CANCEL_REVERT · PAYMENT · REFUND · SETTLEMENT |
| 지갑 동결 | Wallet.frozenAt | 탈퇴한 회원의 지갑에서 돈이 나가는 요청을 막은 상태. 잔액 · 입금은 그대로 |
| 충전 | Charge | 토스로 예치금을 넣는 요청. 상태 PENDING · CONFIRMING(승인 중) · DONE · CANCELLING · CANCELLED · FAILED · EXPIRED |
| 결제 | Payment | 주문 한 건의 예치금 결제. 상태 COMPLETED · FAILED · PARTIALLY_REFUNDED · REFUNDED |
| 환불 | Refund · RefundItem | 주문 전체 취소(결제당 한 번) 또는 판매자 부분 취소(취소당 한 번)의 예치금 환불과 주문상품별 금액 |
| 지급 | Payout | 정산서 한 건의 판매자 지갑 입금 결과(COMPLETED · FAILED). 정산서 ID당 하나 |
| 취소된 주문 기록 | CancelledOrder | 결제 전에 취소된 주문. 뒤늦게 온 결제 요청을 차감하지 않으려고 남김 |
| 아웃박스 | PaymentOutboxMessage | 보낼 이벤트를 업무와 같은 트랜잭션에 저장한 표. 릴레이가 Kafka로 보내고 보낸 시각을 남김 |
| DLT | {토픽}.{받는도메인}.dlt | 받는 쪽이 여러 번 시도해도 처리하지 못한 메시지를 모아 두는 토픽 |
| 수신 스위치 | kidly.{도메인}.kafka.consumer.enabled | Kafka로 받을지 Spring 이벤트로 받을지 고르는 설정값. 장애 때 되돌리는 데 씀 |
| 검색 문서 | SearchProduct | product.changed 하나로 만든 Elasticsearch 상품 문서(표시 가격 · 품절 · 항목 값) |
| 검색 판매자 | SearchSeller | 상호명만 담은 판매자 문서. 판매자가 바뀌어도 상품 문서를 고치지 않으려고 따로 둠 |
| 처음 채우기 | - | 새로 만든 색인이 비어 있어, 상품 · 판매자가 전체를 이벤트로 다시 보내 채우는 것 |
| 형태소 분석 | nori | 한글 검색어를 뜻 단위로 나눠, 띄어쓰기 · 조사 · 어순이 달라도 찾게 하는 Elasticsearch 분석기 |
| 활동 기록 | ActivityRecord | 추천이 받는 사용자 행동 4종(상세 진입 · 검색어 · 장바구니 담기와 빼기 · 구매자 취소)의 저장 단위 |
| 선호 점수 | PreferenceSnapshot | 자녀(또는 회원)마다 하루 한 번 계산하는 카테고리 · 브랜드 친밀도와 취향 벡터. 요청 때 자녀 몫에 회원 몫을 0.5배로 합쳐 상품 점수를 매기고, 더보기 목록 정렬과 AI 후보 선정에 씀 |
| 친밀도 | CategoryAffinity · BrandAffinity | 최근 활동이 한 카테고리 · 브랜드에 얼마나 몰렸는지(0 ~ 1). 활동 무게 = 강도 × 최근성이고, 친밀도 = 1 − Π(1 − 무게). 브랜드에는 구매확정도 넣음 |
| 취향 벡터 | TasteVector | 최근 30일에 보고 담은 상품의 임베딩을 활동 무게로 더해 길이 1로 맞춘 벡터. 후보 상품 임베딩과의 코사인 유사도가 상품 점수의 주 신호 |
| 후보 | candidates | AI에게 주는 고를 수 있는 목록. 사이즈는 상품의 실제 사이즈, 상품은 규칙과 벡터로 거른 20개 |
| 출처 | source | 응답을 AI가 만들었는지(AI) 규칙으로 대신했는지(RULE) |
| 임베딩 | embedding | 상품 글을 숫자 벡터로 바꾼 값. 뜻이 비슷한 상품끼리 가까운 값이 됨 |
| 벡터 저장소 | vector store | 임베딩을 저장하고 가까운 순으로 찾는 저장소 |
| AI 응답 저장 | AiResponse (`ai_response`) | 응답 종류와 입력 해시를 키로 AI 응답을 저장한 표. 같은 종류 · 같은 입력이면 24시간 재사용하고, 덮어쓰지 않고 줄을 더해 응답 로그를 겸함. 30일이 지난 줄은 매일 지움 |
| 기준 옷 | ReferenceGarment | 자녀가 지금 잘 맞게 입는 옷. 자녀마다 상의 · 바지 · 아우터 · 원피스 · 치마 칸에 한 벌씩, 실측을 cm로 저장(2순위) |
| 기준 핏 | - | 기준 옷의 실측과 상품 실측의 차이(예: 가슴 +2cm)(2순위) |
| 상품 실측 | SizeSpec.measurements | 판매자가 사이즈마다 적은 옷 치수(세미에 이미 있음) |
| 공용 장바구니 | Wish (주문 wish 컨텍스트) | 부모 · 자녀가 같이 보는 자녀별 장바구니. 개인 장바구니와 별도 |
| 조르기 | WishRequest | 자녀가 공용 장바구니 상품을 보내 부모의 승인 · 거절을 받는 요청 |
| 판매자 부분 취소 | order-item.cancelled · cancellationId | 판매자가 발송 전 자기 몫(배송 한 건)을 취소한 것. 취소마다 ID가 있어 환불 · 재고 복구가 한 번만 일어남 |
| 리뷰 | Review | 구매확정한 주문상품에 남기는 별점(1~5)과 본문. 주문상품마다 1개 |
| 작성 자격 | ReviewEligibility | 리뷰를 쓸 수 있는 주문상품. 구매확정 이벤트로 만든다 |
| 찜 | favorite | 회원이 관심 상품을 표시해 둔 것. 회원 × 상품마다 1개 (조르기와 다름) |
| 표시 상태 | displayStatus | ON_SALE(판매 중) · SOLD_OUT(품절) · SALE_ENDED(판매 종료). 커뮤니티 화면용 |
| 사이즈 핏 | fit | SMALL(작다) · TRUE_TO_SIZE(맞다) · LARGE(크다). 리뷰에 남기는 크기 의견(선택, 구매 사이즈가 있는 주문상품만) |
| 검사 상태 | moderationStatus | PENDING(검사 대기 · 공개) · PASSED(통과 · 공개) · HIDDEN(검사에서 숨김, 관리자 판단 대기) · CONFIRMED_HIDDEN(관리자 숨김 확정) · RESTORED(관리자 다시 공개) |
| 노출 | visibility | 리뷰 목록 항목의 PUBLIC(공개) · UNDER_REVIEW(숨겨짐: 검사 숨김 · 숨김 확정, 작성자의 내 리뷰에만 보임) |
