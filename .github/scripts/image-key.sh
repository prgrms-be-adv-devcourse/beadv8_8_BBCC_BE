#!/usr/bin/env bash
# 앱 이미지에 들어가는 소스만으로 키를 만든다(#1169). 키가 같으면 이미지 내용이 같다고 보고 다시 빌드 · 배포하지 않는다
# 사용: image-key.sh <앱 폴더 이름> [커밋, 기본 HEAD]. 출력: 64자 sha256
# 넣는 것: 이미지 빌드 방식(Dockerfile · .dockerignore), Gradle 설정, 그 앱 폴더, 그 앱이 project(":...") 로 가져오는 모듈(따라가며)
# 이 목록에 없는 폴더(docs · deploy · config(Checkstyle) · architecture(ArchUnit) 등)는 jar 에 들어가지 않는다
set -euo pipefail
app=$1
rev=${2:-HEAD}
[[ -n "$(git ls-tree -d --name-only "$rev" "apps/$app")" ]] || { echo "apps/$app 이 $rev 에 없습니다" >&2; exit 1; }

paths=(Dockerfile .dockerignore "apps/$app")
if git cat-file -e "$rev:apps/$app/settings.gradle.kts" 2>/dev/null; then
  # 자체 빌드(member-service): 그 폴더의 wrapper · 설정과 includeBuild 하는 shared 만 쓴다
  paths+=(shared)
else
  paths+=(settings.gradle.kts gradle.properties gradle gradlew build-logic)
  # project(":common") · project(":domains:order") 처럼 가져오는 모듈을 따라간다(도메인 → common → shared)
  queue=("apps/$app")
  seen=" apps/$app "
  while ((${#queue[@]})); do
    module=${queue[0]}
    queue=("${queue[@]:1}")
    for dep in $(git show "$rev:$module/build.gradle.kts" 2>/dev/null | grep -oE 'project\(":[a-zA-Z0-9:_-]+"\)' | sed -E 's/project\(":([^"]+)"\)/\1/; s#:#/#g' | sort -u); do
      [[ "$seen" == *" $dep "* ]] && continue
      seen+="$dep "
      paths+=("$dep")
      queue+=("$dep")
    done
  done
  # 도메인 build.gradle.kts 가 의존을 적지 않고 build-logic 규칙으로 common · shared 를 받는 경우도 있어 늘 넣는다
  paths+=(common shared)
fi

# IMAGE_KEY_PATHS=1 이면 넣은 경로를 stderr 로 보여 준다(확인용)
[[ -z "${IMAGE_KEY_PATHS:-}" ]] || printf '%s\n' "${paths[@]}" | sort -u | tr '\n' ' ' >&2
# 경로마다 그 커밋의 git 객체 해시(폴더는 트리 해시)를 적어 합친다. 없는 경로는 none
for path in $(printf '%s\n' "${paths[@]}" | sort -u); do
  printf '%s %s\n' "$path" "$(git rev-parse --verify --quiet "$rev:$path" || echo none)"
done | { echo "image-key v1 $app"; cat; } | sha256sum | cut -d' ' -f1
