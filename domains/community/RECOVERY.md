# 커뮤니티 수신 복구

## DLT에 원본이 남아 있는 경우

1. `GET /api/v1/admin/community/dead-letters`로 OPEN 목록을 본다. 상세는 `/{id}`다.
2. 원래 토픽·위치, 이벤트 ID, 실패 종류로 원인을 확인하고 DB 장애 또는 입력 처리 문제를 해결한다.
3. 관리자 인증으로 `POST /api/v1/admin/community/dead-letters/{id}/reprocess`를 호출한다.
4. `status=REPROCESSED`와 처리자·시각을 확인한다. 실패는 `OPEN`과 `lastReplayFailureType`으로 응답한다.
5. 리뷰 작성 가능 목록에서 해당 주문상품을 확인한다. 이미 성공한 DLT의 중복 요청은 재실행하지 않는다.

목록·상세에는 원본 본문과 예외 메시지/스택을 응답하지 않는다. 본문은 제어문자도 보존할 수 있도록 DB bytea로 저장한다.
같은 DLT 토픽·파티션·오프셋은 한 행만 남긴다. 저장 실패 시 소비자는 완료 위치를 넘기지 않고 계속 재시도한다.
재처리가 겹쳐도 작성 자격은 주문상품 ID 유일 제약으로 한 건이며, 이미 성공한 결과를 늦은 실패가 덮어쓰지 않는다.
반영과 처리 결과 기록은 별도 트랜잭션이다. 반영 후 결과 저장만 실패했으면 OPEN으로 남고 해당 시도의 횟수·처리자 기록도 남지 않는다. 다시 요청해도 중복 자격은 생기지 않는다.
깨진/미지원 원본은 성공으로 표시하지 않는다. 원본 편집과 임의 폐기 API는 제공하지 않는다.

## 원본의 Kafka 보존 기간이 지난 경우

주문이 소유한 확정 사실을 기존 이벤트 계약으로 다시 발행한다. 다른 도메인의 DB나 소비 그룹 오프셋을 수정하지 않는다.

1. 관리자 인증으로 `POST /api/v1/admin/orders/purchase-confirmed-events?afterOrderItemId=0&size=100`을 호출한다.
2. 첫 응답의 `throughOrderItemId`를 고정한다.
3. `hasNext=true`면 `nextAfterOrderItemId`를 다음 요청의 `afterOrderItemId`로 넣고 같은 상한을 보낸다.
4. `hasNext=false`까지 반복한다. 실패한 배치는 같은 커서로 다시 요청한다.
5. 응답의 `published`는 아웃박스 저장 수다. 주문 아웃박스 전송 상태와 커뮤니티 자격 반영을 따로 확인한다.

재발행은 CONFIRMED이고 confirmedAt이 있는 주문상품만 선택한다. 원래 주문·상품·회원 ID, 금액, 확정 시각을 유지한다.
새 eventId로 동일 사실을 재전달하므로 정산·추천·커뮤니티의 주문상품 ID 기준 멱등 처리를 전제로 한다.
현재 수신 코드가 해당 기준을 사용하며, API와 이벤트에는 특정 수신 도메인용 필드를 추가하지 않는다.
스캔 도중 작은 ID가 뒤늦게 확정되면 정상 구매확정 이벤트로 전달된다. 장애가 계속됐다면 범위를 다시 스캔한다.

## 보존과 운영 확인

Kafka DLT의 retention은 브로커/토픽 설정을 따른다. 이 변경은 기존 토픽의 retention을 바꾸지 않는다.
실제 환경의 `retention.ms`/`log.retention.hours`를 확인한다. DB 보관 소비자가 해당 기간보다 오래 중단되면 위 주문 복구 경로를 쓴다.
DB DLT 보관 행은 자동 삭제하지 않는다. OPEN과 REPROCESSED는 상태 필터로 구분한다.
수신 실패/DLT 보관은 ERROR, 재처리 실패는 WARN으로 원본 없이 기록한다. 보관의 반복 실패는 첫 ERROR 뒤 DEBUG로 낮춘다.
