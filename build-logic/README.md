# build-logic

모든 모듈이 같이 쓰는 빌드 설정(convention plugin)을 모아 둔 Gradle **included build**다. 루트 `settings.gradle.kts`가 `pluginManagement { includeBuild("build-logic") }`로 불러오고, 각 모듈은 `plugins { id("kidly.xxx") }` 한 줄로 쓴다.

기준: 도메인 하나가 나중에 서비스 하나(`apps/xxx-service`)로 뜬다. 그때 **도메인 모듈은 고치지 않고 서비스 모듈만 만들면 되게** 한다.

## 플러그인

| id | 적용 대상 | 하는 일 |
|---|---|---|
| `kidly.java-conventions` | 모든 모듈 (아래 플러그인이 포함) | Java 25 toolchain, Checkstyle(루트 `config/checkstyle`), Lombok, Boot BOM, 단위 테스트 의존, `-parameters` 컴파일, JUnit Platform |
| `kidly.library` | `shared`, `common` | `java-library`. 공개 시그니처에 나오는 의존을 `api`로 넘길 수 있게 한다 |
| `kidly.domain` | `domains/*` | `kidly.library` + `common` · `shared` + 도메인 공통 스택(webmvc · validation · data-jpa · QueryDSL) + `java-test-fixtures` |
| `kidly.boot-app` | `apps/*` | Spring Boot 플러그인(`bootJar` · `bootRun`), 테스트 태스크 분리(`test` · `integrationTest` · `functionalTest`), 실행 · 테스트 작업 디렉터리를 루트로 |

- 버전은 루트의 `gradle/libs.versions.toml`(version catalog) 한 곳에 있다. 이 빌드도 `settings.gradle.kts`에서 같은 파일을 읽는다
- 저장소는 루트 `settings.gradle.kts`의 `dependencyResolutionManagement`에서만 선언한다. 모듈 빌드 파일에 `repositories {}`를 넣으면 빌드가 실패한다(`FAIL_ON_PROJECT_REPOS`)
- `archTest`는 플러그인이 아니라 `apps/kidly-app/build.gradle.kts`에 있다 (아래 4)

## 왜 이렇게 했나

멀티모듈은 MSA로 가는 단계다(`wiki/멀티모듈 분리와 MSA 전환 가이드.md`). 서비스는 나중에 따로 뜨지만 레포는 하나로 둔다. 빌드 설정은 이 전제에서 골랐다.

### 1. 루트 `subprojects {}` 대신 convention plugin

| | `subprojects {}` | convention plugin |
|---|---|---|
| 모듈에 무엇이 적용됐나 | 루트 파일을 봐야 안다. 바깥에서 주입한다 | 모듈 파일의 `id("kidly.domain")`에 드러난다 |
| 모듈마다 다른 설정 | `if (name == ...)` 분기가 쌓인다 | 플러그인을 나눈다 (`domain` · `boot-app`) |
| 서비스를 늘릴 때 | 루트 분기를 고친다 | 새 모듈에 `id("kidly.boot-app")` 한 줄 |

도메인 담당자가 빌드 파일을 고치지 않고 코드만 옮길 수 있게 하는 것이 목적이었다. 도메인 모듈 빌드 파일은 거의 `plugins { id("kidly.domain") }`뿐이다.

### 2. `buildSrc` 대신 `build-logic` (included build)

처음에는 `buildSrc`로 만들었다가 바꿨다.

1. **Gradle이 권장하는 방식이다.** Gradle 문서(Best Practices for Structuring Builds, "Favor `build-logic` Composite Builds")가 convention plugin은 `buildSrc` 대신 included build에 두라고 한다
2. **독립된 빌드라 그대로 배포할 수 있다.** `kotlin-dsl`이 이미 `java-gradle-plugin`을 적용하므로, 레포를 나누게 되면 `maven-publish`만 더해 플러그인으로 배포하고 다른 레포가 받아 쓸 수 있다. `buildSrc`는 이 빌드 안에서만 쓴다
3. **클래스로더가 분리된다.** `buildSrc`는 모든 빌드 스크립트의 부모 클래스로더에 들어가 한 줄만 바꿔도 빌드 전체가 out-of-date가 된다. included build의 플러그인은 외부 의존처럼 그 플러그인을 적용한 모듈에만 들어간다. 다만 이 레포는 모든 모듈이 `kidly.*`를 쓰므로 지금 체감 차이는 작다

대가: 루트 `settings.gradle.kts`의 `includeBuild` 한 줄과 이 폴더의 `settings.gradle.kts`가 더 필요하다.

### 3. 버전은 Kotlin 상수 대신 version catalog

- IntelliJ가 `libs.xxx`를 자동 완성하고 정의로 이동한다
- Dependabot이 `libs.versions.toml`의 버전을 읽고 올려 줄 수 있다. Kotlin 객체(`Versions.kt`)는 읽지 못한다
- 모듈과 `build-logic`이 같은 파일을 읽어 플러그인 버전까지 한 곳에서 관리한다
- Boot BOM이 관리하는 starter는 버전 없이 빌드 파일에 문자열로 쓴다. catalog에는 BOM 밖의 버전만 둔다

### 4. 테스트 태스크는 앱(서비스) 모듈에만

| 태스크 | 위치 | 이유 |
|---|---|---|
| `test` | 모든 모듈 | 단위 테스트. 도메인 모듈의 `test`는 태그를 빼지 않는다: 태그 붙은 테스트가 도메인 모듈에 잘못 들어오면 돌릴 태스크가 없어 조용히 빠지기보다 `test`에서 드러나는 편이 낫다 |
| `integrationTest` · `functionalTest` (태그) | `kidly.boot-app` | 전체 컨텍스트 · 마이그레이션 · test 리소스가 있는 앱(서비스)에서만 돈다. 앱의 `test`는 이 태그를 뺀다 |
| `archTest` | `apps/kidly-app/build.gradle.kts` | 모든 모듈 코드가 모이는 앱에서 경계 규칙을 검사한다. 플러그인에 두면 테스트가 없는 서비스 모듈의 `check`가 `No tests found`로 실패한다 |

CI는 루트에서 `./gradlew archTest` · `test` · `integrationTest` · `functionalTest`를 부른다. 이름으로 부르면 그 태스크가 있는 모듈에서만 돈다.

### 5. 도메인 공통 스택은 모든 도메인이 쓰는 것만

`kidly.domain`에는 모든 도메인 코드가 쓰는 것(webmvc · validation · data-jpa · QueryDSL)만 넣는다. 한 도메인만 쓰는 것은 그 도메인 빌드 파일에 둔다: member=JWT 발급(resource-server), notification=mail, product=AWS S3, settlement=batch. Swagger UI(springdoc)는 실행되는 앱(서비스)이 화면을 여는 것이라 앱 모듈에 둔다.

## 도메인 옮기는 법

빌드 파일은 고치지 않는다. `git mv`만 한다(이력이 따라온다). `{d}`는 도메인 이름, 경로의 `java/com/bbcc/kidly/boundedcontext/{d}`는 그대로 둔다.

| 무엇 | 어디서 | 어디로 |
|---|---|---|
| main 코드 | `apps/kidly-app/src/main/java/.../boundedcontext/{d}` | `domains/{d}/src/main/java/...` |
| 단위 테스트 (태그 없는 것) | `apps/kidly-app/src/test/java/.../boundedcontext/{d}` | `domains/{d}/src/test/java/...` |
| Fixture · 테스트 헬퍼 중 **단위 테스트가 쓰는 것** (와 그것이 쓰는 헬퍼) | 같은 곳 | `domains/{d}/src/testFixtures/java/...` |
| 도메인 전용 리소스 (`{d}.yml`, `data/{d}/**`) | `apps/kidly-app/src/main/resources` | `domains/{d}/src/main/resources` |
| 통합 · 기능 테스트(`@IntegrationTest` · `@FunctionalTest`)와 그것만 쓰는 헬퍼 | | 앱에 남긴다 |

- 앱에 남는 것: `KidlyApplication`, `application*.yml`, `logback-spring.xml`, `db/migration`(기존 파일), `support` · `scenario` · `architecture` 테스트, test 리소스
- 테스트 Fixture는 `src/testFixtures`에 두면 그 도메인 단위 테스트와 앱(나중엔 서비스)의 통합 테스트가 같이 쓴다. 앱은 모든 도메인의 `testFixtures(...)`를 이미 의존한다
- 단위 테스트는 자기 도메인 코드 · `common` · `shared` · 자기 Fixture만 쓸 수 있다. 다른 도메인의 클래스나 Fixture를 쓰는 테스트는 도메인 사이 테스트 의존이 생기므로 앱에 남긴다
- 앱 `application.yml`의 `spring.config.import: classpath:{d}.yml`은 jar 안도 찾으므로 고치지 않는다
- 옮긴 뒤 확인: `./gradlew build` (테스트 수가 옮기기 전과 같은지)
- 다 옮긴 도메인이 생기면 앱 빌드 파일에서 "xxx가 옮겨지면 지운다" 주석이 붙은 의존을 지운다(프로젝트 관리자)

## 마이그레이션 위치

- 이미 있는 파일은 `apps/kidly-app/src/main/resources/db/migration`에 그대로 둔다. 적용된 파일은 옮기거나 고치지 않는다
- **새 마이그레이션은 도메인 모듈** `domains/{d}/src/main/resources/db/migration/{스키마}/V{yyyyMMddHHmm}__설명.sql`에 둔다 (예: `domains/order/.../db/migration/orders/`)
- Flyway는 `spring.flyway.locations: classpath:db/migration`으로 모든 jar(`bootJar`의 `BOOT-INF/lib/*.jar` 포함)의 `db/migration`과 그 하위 폴더를 읽는다. 버전은 폴더와 상관없이 전체에서 하나로 정렬된다
- `MigrationNamingTest`는 `classpath*:db/migration/**/*.sql`로 같은 범위의 이름 · 버전 중복을 검사한다

## IntelliJ

- 앱 실행은 공유 실행 구성 `KidlyApplication`(`.run/KidlyApplication.run.xml`)을 쓴다. 작업 디렉터리가 프로젝트 루트라 `config/jwt` · `.env`를 찾는다. 새로 만든 구성은 작업 디렉터리가 모듈 폴더(`apps/kidly-app`)가 되어 실패한다
- 테스트 러너는 Gradle로 둔다(Settings > Build Tools > Gradle > Run tests using: Gradle). 작업 디렉터리 · 태그 · test fixtures 설정이 Gradle 태스크에만 있다

## 알아 둘 것

- **convention plugin 안에서는 `libs.xxx` 접근자가 생기지 않는다.** 이름으로 찾는다

  ```kotlin
  val libs = the<VersionCatalogsExtension>().named("libs")
  implementation(libs.findLibrary("querydsl-jpa").get())
  ```

  모듈 빌드 파일(`common/build.gradle.kts` 등)에서는 `libs.querydsl.jpa`처럼 그냥 쓴다
- QueryDSL apt의 `:jpa` classifier는 catalog에 넣을 수 없어 쓰는 곳에서 붙인다: `variantOf(libs.querydsl.apt) { classifier("jpa") }`
- `common`이 `api`로 내보내는 의존(webmvc · data-jpa · resource-server)은 도메인 모듈 컴파일 클래스패스에도 들어간다
- ArchUnit은 테스트 클래스패스의 모듈 jar까지 검사하므로 도메인 모듈 코드도 앱의 `archTest`에서 검사된다. `*-test-fixtures.jar`는 `DoNotIncludeTestFixtures`로 뺀다(테스트 코드라서)
- Boot 플러그인이 없는 모듈은 `-parameters` 없이 컴파일되어 `@PathVariable` · `@Query`의 이름 생략 바인딩이 실패하므로 `kidly.java-conventions`가 넣는다
- 설정의 상대 경로(`file:config/jwt/...`, `.env`)가 루트 기준이라 `bootRun`과 테스트는 루트에서 실행된다(`kidly.boot-app`)

## 자주 하는 일

| 하려는 것 | 고칠 곳 |
|---|---|
| 라이브러리 버전 올리기 | `gradle/libs.versions.toml`의 `[versions]` |
| BOM 밖 라이브러리 추가 | `libs.versions.toml`의 `[libraries]`에 넣고 쓰는 모듈 빌드 파일에서 `libs.xxx` |
| 한 도메인만 쓰는 의존 추가 | 그 도메인의 `build.gradle.kts` (예: `domains/product`의 S3) |
| 모든 도메인에 의존 추가 | `kidly.domain.gradle.kts` |
| 서비스(실행 모듈) 추가 | `apps/xxx-service/build.gradle.kts`에 `id("kidly.boot-app")`, 도메인 모듈 `implementation`과 `testImplementation(testFixtures(...))`, 런타임 의존(postgresql · flyway · actuator 등). 루트 `settings.gradle.kts`에 `include`. 이미지는 `docker build --build-arg APP=xxx-service .` |

빌드 설정 파일은 git 규칙에 따라 프로젝트 관리자가 고친다. 바꿔야 하면 먼저 알린다.
