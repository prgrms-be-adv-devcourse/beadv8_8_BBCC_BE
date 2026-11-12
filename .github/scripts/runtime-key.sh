#!/usr/bin/env bash
# 실행 jar 가 실제로 쓰는 코드만으로 키를 만든다(#1192). 키가 같으면 실행 내용이 같다고 보고 서비스를 다시 띄우지 않는다
# 사용: runtime-key.sh <bootJar>. 출력: 64자 sha256. JDK 25(jdeps · jar)가 있는 곳에서 돌린다
# 넣는 것
#   우리 코드(com.bbcc.kidly)의 클래스 · 리소스 전부. 단 shared 클래스는 그 앱이 따라가며 참조하는 것만(jdeps)
#     common 은 Spring 이 패키지를 훑어 설정 클래스를 찾아 참조 없이도 실행에 들어가므로 줄이지 않는다
#     shared 는 JDK 만 쓰는 계약(record · enum · interface)이라 참조하지 않는 클래스는 실행에 영향이 없다
#   외부 라이브러리 jar(파일 내용), 실행 설정(MANIFEST · classpath.idx · layers.idx), Spring Boot loader
# 우리 jar 는 시각이 고정(1980)되지만 jar 파일 대신 안의 파일 내용으로 해시해 압축 방식 차이에도 흔들리지 않게 한다
# RUNTIME_KEY_SHARED=1 이면 넣은 shared 클래스를 stderr 로 보여 준다(확인용)
set -euo pipefail
jar=$(realpath "$1")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
mkdir boot own
(cd boot && jar xf "$jar")
[[ -f boot/META-INF/MANIFEST.MF && -d boot/BOOT-INF ]] || { echo "Spring Boot jar 가 아닙니다: $1" >&2; exit 1; }

lines=$work/lines
: > "$lines"
roots=(boot/BOOT-INF/classes)
for lib in boot/BOOT-INF/lib/*.jar; do
  name=$(basename "$lib")
  if jar tf "$lib" | grep -q '^com/bbcc/kidly/'; then
    dir=own/${name%.jar}
    mkdir -p "$dir"
    (cd "$dir" && jar xf "$work/$lib")
    roots+=("$dir")
  else
    echo "lib $name $(sha256sum < "$lib" | cut -d' ' -f1)" >> "$lines"
  fi
done

# 우리 클래스끼리의 참조(같은 패키지 포함)를 모두 뽑아 shared 밖의 클래스에서 따라간다
jdeps -verbose:class -filter:none --ignore-missing-deps -e 'com\.bbcc\.kidly\..*' "${roots[@]}" \
  | awk '$2 == "->" && $1 ~ /^com\.bbcc\.kidly\./ && $3 ~ /^com\.bbcc\.kidly\./ { print $1, $3 }' | sort -u > edges
[[ -s edges ]] || { echo "jdeps 가 참조를 하나도 찾지 못했습니다" >&2; exit 1; }
reachable=$(awk '
  { to[$1] = to[$1] " " $2; from[$1] = 1 }
  END {
    for (c in from) if (c !~ /^com\.bbcc\.kidly\.shared\./) { queue[++n] = c; seen[c] = 1 }
    for (i = 1; i <= n; i++) {
      k = split(to[queue[i]], next_, " ")
      for (j = 1; j <= k; j++) if (!(next_[j] in seen)) { seen[next_[j]] = 1; queue[++n] = next_[j] }
    }
    for (c in seen) if (c ~ /^com\.bbcc\.kidly\.shared\./) print c
  }' edges | sort)
[[ -z "${RUNTIME_KEY_SHARED:-}" ]] || echo "$reachable" >&2
keep=$work/keep
echo "$reachable" | sed 's#\.#/#g; s#$#.class#' > "$keep"

# 파일마다 "<구분> <경로> <sha256>". shared 클래스는 위에서 고른 것만 남긴다
for root in "${roots[@]}"; do
  label=${root#own/}
  (cd "$root" && find . -type f ! -path './META-INF/MANIFEST.MF' -print0 | xargs -0 sha256sum) \
    | awk -v label="$label" -v keep="$keep" '
        BEGIN { while ((getline k < keep) > 0) kept[k] = 1 }
        { path = substr($2, 3) }
        path ~ /^com\/bbcc\/kidly\/shared\/.*\.class$/ && !(path in kept) { next }
        { print "own", label "/" path, $1 }' >> "$lines"
done

# 라이브러리 · 우리 클래스 밖의 나머지(MANIFEST · idx · loader)
(cd boot && find . -type f ! -path './BOOT-INF/lib/*' ! -path './BOOT-INF/classes/*' -print0 | xargs -0 sha256sum) \
  | awk '{ print "boot", substr($2, 3), $1 }' >> "$lines"

{ echo "runtime-key v1"; LC_ALL=C sort "$lines"; } | sha256sum | cut -d' ' -f1
