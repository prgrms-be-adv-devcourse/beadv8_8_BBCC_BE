# build-logic

모든 모듈이 같이 쓰는 빌드 설정(convention plugin)을 모아 둔 Gradle **included build**다. 루트 `settings.gradle.kts`가 `pluginManagement { includeBuild("build-logic") }`로 불러오고, 각 모듈은 `plugins { id("kidly.xxx") }` 한 줄로 쓴다.

## 플러그인

| id | 적용 대상 | 하는 일 |
|---|---|---|
| `kidly.java-conventions` | 모든 모듈 (아래 플러그인이 포함) | Java 25 toolchain, Checkstyle(루트 `config/checkstyle`), Lombok, Boot BOM, 단위 테스트 의존, 테스트 태스크 분리(`test` · `integrationTest` · `functionalTest`) |
| `kidly.library` | `shared`, `common` | `java-library`. 공개 시그니처에 나오는 의존을 `api`로 넘길 수 있게 한다 |
| `kidly.domain` | `domains/*` | `kidly.library` + `common` · `shared` + 도메인 공통 스택(webmvc · validation · security · resource-server · data-jpa · QueryDSL · springdoc) |
| `kidly.boot-app` | `apps/*` | Spring Boot 플러그인(`bootJar` · `bootRun`), `archTest`, 실행 · 테스트 작업 디렉터리를 루트로 |

버전은 루트의 `gradle/libs.versions.toml`(version catalog) 한 곳에 있다. 이 빌드도 `settings.gradle.kts`에서 같은 파일을 읽는다.

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

- **레포를 나누게 되어도 그대로 쓸 수 있다.** `build-logic`은 독립된 Gradle 빌드라 `java-gradle-plugin`을 붙이면 플러그인으로 배포해 다른 레포가 받아 쓸 수 있다. `buildSrc`는 이 레포 안에서만 쓸 수 있다
- **고칠 때 영향이 작다.** `buildSrc`는 모든 빌드 스크립트의 클래스패스에 들어가서 한 줄만 바꿔도 프로젝트 전체를 다시 설정한다. included build는 플러그인을 쓰는 곳에만 들어간다
- 대가: `settings.gradle.kts`의 `includeBuild` 한 줄과 이 폴더의 `settings.gradle.kts`가 더 필요하다

### 3. 버전은 Kotlin 상수 대신 version catalog

- IntelliJ가 `libs.xxx`를 자동 완성하고 정의로 이동한다
- Dependabot이 `libs.versions.toml`의 버전을 읽고 올려 줄 수 있다. Kotlin 객체(`Versions.kt`)는 읽지 못한다
- 모듈과 `build-logic`이 같은 파일을 읽어 플러그인 버전까지 한 곳에서 관리한다
- Boot BOM이 관리하는 starter는 버전 없이 빌드 파일에 문자열로 쓴다. catalog에는 BOM 밖의 버전만 둔다

## 알아 둘 것

- **convention plugin 안에서는 `libs.xxx` 접근자가 생기지 않는다.** 이름으로 찾는다

  ```kotlin
  val libs = the<VersionCatalogsExtension>().named("libs")
  implementation(libs.findLibrary("querydsl-jpa").get())
  ```

  모듈 빌드 파일(`common/build.gradle.kts` 등)에서는 `libs.querydsl.jpa`처럼 그냥 쓴다
- QueryDSL apt의 `:jpa` classifier는 catalog에 넣을 수 없어 쓰는 곳에서 붙인다: `variantOf(libs.querydsl.apt) { classifier("jpa") }`
- `archTest`는 `kidly.boot-app`에만 있다. ArchUnit이 테스트 클래스패스의 모듈 jar까지 검사하므로 도메인 모듈 코드도 앱 모듈에서 검사된다
- 설정의 상대 경로(`file:config/jwt/...`, `.env`)가 루트 기준이라 `bootRun`과 테스트는 루트에서 실행된다(`kidly.boot-app`)

## 자주 하는 일

| 하려는 것 | 고칠 곳 |
|---|---|
| 라이브러리 버전 올리기 | `gradle/libs.versions.toml`의 `[versions]` |
| BOM 밖 라이브러리 추가 | `libs.versions.toml`의 `[libraries]`에 넣고 쓰는 모듈 빌드 파일에서 `libs.xxx` |
| 한 도메인만 쓰는 의존 추가 | 그 도메인의 `build.gradle.kts` (예: `domains/product`의 S3) |
| 모든 도메인에 의존 추가 | `kidly.domain.gradle.kts` |
| 서비스(실행 모듈) 추가 | `apps/xxx-service/build.gradle.kts`에 `id("kidly.boot-app")`와 필요한 도메인 모듈 의존, 루트 `settings.gradle.kts`에 `include` |

빌드 설정 파일은 git 규칙에 따라 프로젝트 관리자가 고친다. 바꿔야 하면 먼저 알린다.
