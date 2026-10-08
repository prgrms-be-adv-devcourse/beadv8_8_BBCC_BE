# Kidly BE

자녀 맞춤 키즈 패션 이커머스. Spring Boot 4.1 / Java 25 / PostgreSQL 18 모듈러 모놀리스.

## 명령어
- 로컬 DB: `docker compose up -d` (pgAdmin: http://localhost:5050, 서버 비밀번호 `kidly`)
- 실행: `./gradlew bootRun` (기본 프로필 local)
- 빌드(Checkstyle, ArchUnit, 테스트 포함): `./gradlew build`
- 경계 규칙만 검사: `./gradlew archTest` (`test`에서는 제외된다)
- Swagger: http://localhost:8080/swagger-ui.html

## 구조
- `com.bbcc.kidly.global`: 공통 코드. 도메인 패키지를 참조하지 않는다
- 도메인: `member`, `product`, `recommend`, `order`, `payment`, `settlement`
  - `api`: 다른 도메인에 공개하는 인터페이스·DTO·이벤트. 다른 도메인은 이 패키지만 참조한다
  - `presentation`(컨트롤러, `dto`의 XxxRequest/XxxResponse), `application`, `domain`, `infrastructure`
- 직접 호출은 세 곳만: 주문→상품(재고 선점), 추천→회원(자녀 프로필), 추천→상품(권장 키 범위·판매 중 상품). 나머지는 Spring 이벤트
- 각 도메인은 자기 DB 스키마만 쓴다: `member`, `product`, `recommend`, `orders`, `payment`, `settlement`
- 경계 규칙은 `src/test/java/com/bbcc/kidly/architecture/ArchitectureTest.java`에서 검사한다

## 코드 컨벤션
- TDD (RED → GREEN → REFACTOR)
- camelCase: 변수·메서드 / PascalCase: 클래스 / UPPER_SNAKE_CASE: 상수 / snake_case: DB 테이블·컬럼
- 엔티티에 `@Data`, public setter 금지. 상태는 업무 메서드로만 바꾼다
- 마이그레이션 도구(Flyway)는 쓰지 않는다. `ddl-auto`는 로컬 update, 그 외 validate

## DB 명명
- PK `id`, FK `참조테이블단수_id`, 불리언 `is_/has_/can_`, 일시 `_at`, 날짜 `_on/_date`
- 수량 `_count/_qty`, 금액 `_amount/_price`, 감사 컬럼 `created_at`, `updated_at`, `deleted_at`
- 예약어(order, user, group, key, value 등) 금지, 부정형 불리언 금지

## API 응답
- 리스트는 null 대신 `[]`, 없는 숫자·선택 문자열·중첩 객체는 `null`, Boolean은 non-null
- 단건 조회 결과가 없으면 404
- 목록은 PageResponse: `content, page, size, totalElements, totalPages, hasNext`

## Git
- 브랜치 `feature/도메인/#이슈-기능`, `fix/...`, `hotfix/...`. main·develop 직접 push 금지
- 커밋 `타입: 요약` (feat, fix, docs, style, refactor, test, chore), 제목 50자, 마침표 없음
