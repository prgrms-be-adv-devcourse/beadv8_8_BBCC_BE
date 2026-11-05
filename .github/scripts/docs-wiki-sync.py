#!/usr/bin/env python3
"""docs/ 를 GitHub 위키 형식으로 바꿔 위키 저장소 폴더를 다시 채운다.

사용: docs-wiki-sync.py <docs 폴더> <위키 저장소 폴더> <커밋>

- 위키 저장소 폴더는 .git 만 남기고 비운 뒤 새로 채운다. 위키에서 직접 고친 내용은 사라진다
- 위키 페이지 이름은 경로로 정한다: docs/README.md → Home, {폴더}/README.md → 폴더 이름,
  그 밖 → 폴더-파일(단어 첫 글자 대문자). 이름이 겹치면 실패한다
- 위키는 페이지 이름을 큰 제목으로 보여 주므로 문서 첫 줄의 # 제목은 지우고, 사이드바 이름으로 쓴다
  (날짜가 붙은 제목은 날짜 앞까지. docs/README.md 는 '홈')
- 사이드바는 폴더별로 묶고, 폴더 README 에 링크가 나오는 순서를 따른다
- 제목이 4개 이상인 문서는 첫머리에 가장 큰 절의 목차를 넣는다
- 상대 링크는 위키 페이지 이름 · 위키 저장소 경로로 바꾼다. 대상이 docs/ 에 없으면 실패한다
- 그림 파일은 같은 상대 경로로 복사한다. .vitepress · node_modules · package.json 같은 사이트 파일은 뺀다
- 페이지 끝에 원본 경로 · 커밋을 작게 적고, _Sidebar.md · _Footer.md 를 만든다
"""
import os
import posixpath
import re
import shutil
import sys

# 대문자로 둘 이름. 키는 소문자 단어
NAME_OVERRIDES = {'api': 'API'}
LINK = re.compile(r'(!?)\[([^\]]*)\]\(([^)\s]+)([^)]*)\)')
SCHEME = re.compile(r'^[a-z][a-z0-9+.-]*:', re.IGNORECASE)
TITLE = re.compile(r'^#\s+(.+?)\s*$')
HEADING = re.compile(r'^(#{1,6})\s+(.+?)\s*#*\s*$')
TOC_MIN = 4
CODE = re.compile(r'(```[\s\S]*?```|`[^`\n]*`)')
IMAGE_EXT = ('.png', '.jpg', '.jpeg', '.gif', '.svg', '.webp')
DATED = re.compile(r'\s*·\s*\d{4}-\d{2}-\d{2}.*$')
FOOTER = '[← 홈](Home) · 이 위키는 자동으로 만들어진다 · 수정은 레포의 `docs/`에서 PR로\n'


def word(w):
    return NAME_OVERRIDES.get(w.lower(), w[:1].upper() + w[1:])


def page_name(rel):
    parts = rel[:-len('.md')].split('/')
    if parts == ['README']:
        return 'Home'
    if parts[-1] == 'README':
        parts = parts[:-1]
    return '-'.join(word(w) for w in '-'.join(parts).replace('_', '-').split('-'))


def split_title(body):
    """첫 줄의 # 제목과 그 뒤 본문을 나눈다. 제목이 없으면 (None, 본문)"""
    lines = body.split('\n')
    i = 0
    while i < len(lines) and not lines[i].strip():
        i += 1
    m = TITLE.match(lines[i]) if i < len(lines) else None
    if not m:
        return None, body
    i += 1
    while i < len(lines) and not lines[i].strip():
        i += 1
    return m.group(1), '\n'.join(lines[i:])


def anchor(text):
    # GitHub 제목 앵커: 링크 · 코드 표시를 떼고 소문자, 글자 · 숫자 · 밑줄 · 하이픈 · 공백만 남겨 공백을 하이픈으로
    text = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', text)
    text = re.sub(r'[`*]', '', text).strip().lower()
    return re.sub(r'[^\w\- ]', '', text).replace(' ', '-')


def toc(body):
    found, fence = [], False
    for line in body.split('\n'):
        if line.startswith('```'):
            fence = not fence
            continue
        m = None if fence else HEADING.match(line)
        if m:
            found.append((len(m.group(1)), m.group(2)))
    if len(found) < TOC_MIN:
        return ''
    top = min(level for level, _ in found)
    seen, items = {}, []
    for level, text in found:
        a = anchor(text)
        n = seen.get(a, 0)
        seen[a] = n + 1
        if level == top:
            label = re.sub(r'[`*]', '', text)
            items.append(f'[{label}](#{a if n == 0 else f"{a}-{n}"})')
    if len(items) < 2:
        return ''
    return '**목차** · ' + ' · '.join(items) + '\n\n---\n\n'


def collect(src):
    # 문서 사이트 설정(.vitepress) · 패키지(node_modules · package.json)는 위키에 올리지 않는다. 그림만 함께 옮긴다
    md_files, assets = [], []
    for root, dirs, files in os.walk(src):
        dirs[:] = sorted(d for d in dirs if not d.startswith('.') and d != 'node_modules')
        for f in sorted(files):
            rel = os.path.relpath(os.path.join(root, f), src).replace(os.sep, '/')
            if f.endswith('.md'):
                md_files.append(rel)
            elif f.lower().endswith(IMAGE_EXT):
                assets.append(rel)
    return md_files, assets


def clear(out):
    for entry in os.listdir(out):
        if entry == '.git':
            continue
        path = os.path.join(out, entry)
        if os.path.isdir(path):
            shutil.rmtree(path)
        else:
            os.remove(path)


def convert(src, rel, names, errors):
    base = posixpath.dirname(rel)

    def fix(m):
        bang, text, target, rest = m.groups()
        if SCHEME.match(target) or target.startswith('#'):
            return m.group(0)
        path, _, anchor = target.partition('#')
        resolved = posixpath.normpath(posixpath.join(base, path))
        if resolved.startswith('..') or not os.path.isfile(os.path.join(src, resolved)):
            errors.append(f'docs/{rel}: 없는 대상 {target}')
            return m.group(0)
        if resolved.endswith('.md'):
            new = names[resolved] + (f'#{anchor}' if anchor else '')
        else:
            new = resolved
        return f'{bang}[{text}]({new}{rest})'

    with open(os.path.join(src, rel), encoding='utf-8') as fp:
        title, body = split_title(fp.read())
    # 코드 블록 · 인라인 코드 안의 예시는 링크로 보지 않는다
    parts = CODE.split(body)
    return title, ''.join(part if i % 2 else LINK.sub(fix, part) for i, part in enumerate(parts))


def link_order(src, rel):
    """폴더 README 에 링크가 나오는 순서대로 그 폴더의 md 경로를 돌려준다"""
    base = posixpath.dirname(rel)
    with open(os.path.join(src, rel), encoding='utf-8') as fp:
        targets = [m.group(3).partition('#')[0] for m in LINK.finditer(fp.read())]
    return [posixpath.normpath(posixpath.join(base, t)) for t in targets if t.endswith('.md') and not SCHEME.match(t)]


def sidebar(src, md_files, names, titles):
    def label(rel):
        if names[rel] == 'Home':
            return '홈'
        # 'Kidly 기획서 v2.0 · 2026-11-02 · …' 처럼 날짜가 붙은 제목은 날짜 앞까지만
        return DATED.sub('', titles[rel] or names[rel].replace('-', ' '))

    groups = {}
    for rel in md_files:
        groups.setdefault(rel.split('/')[0] if '/' in rel else '', []).append(rel)
    lines = ['**Kidly 문서**', '']
    top = groups.pop('', [])
    # 폴더 순서는 docs/README.md 에 링크가 나오는 순서. 글이 하나뿐인 폴더는 맨 위 목록에 둔다
    root_order = link_order(src, 'README.md') if 'README.md' in top else []
    folders = sorted(groups, key=lambda f: next((i for i, r in enumerate(root_order) if r.startswith(f + '/')), len(root_order)))
    singles = [f for f in folders if len(groups[f]) == 1]
    lines += [f'- [{label(rel)}]({names[rel]})' for rel in top + [groups[f][0] for f in singles]]
    for folder in (f for f in folders if f not in singles):
        files = groups[folder]
        index = f'{folder}/README.md'
        order = [r for r in link_order(src, index) if r in files] if index in files else []
        rest = sorted(r for r in files if r not in order and r != index)
        head = f'[{label(index)}]({names[index]})' if index in files else folder
        lines += ['', f'**{head}**', '']
        lines += [f'- [{label(rel)}]({names[rel]})' for rel in dict.fromkeys(order + rest)]
    lines += ['', '---', '', '수정은 레포의 `docs/`에서 PR로']
    return '\n'.join(lines) + '\n'


def main():
    if len(sys.argv) != 4:
        sys.exit('사용: docs-wiki-sync.py <docs 폴더> <위키 저장소 폴더> <커밋>')
    src, out, commit = sys.argv[1:]
    md_files, assets = collect(src)

    names = {}
    for rel in md_files:
        name = page_name(rel)
        if name in names.values():
            sys.exit(f'위키 페이지 이름이 겹친다: {name} (docs/{rel})')
        names[rel] = name

    errors, titles, pages = [], {}, {}
    for rel in md_files:
        titles[rel], pages[rel] = convert(src, rel, names, errors)
    if errors:
        sys.exit('깨진 링크가 있어 위키를 바꾸지 않는다:\n' + '\n'.join(errors))

    clear(out)
    for rel, body in pages.items():
        source = f'\n\n<sub>원본 `docs/{rel}` @ `{commit}`</sub>\n'
        with open(os.path.join(out, names[rel] + '.md'), 'w', encoding='utf-8') as fp:
            fp.write(toc(body) + body.rstrip('\n') + source)
    for rel in assets:
        dst = os.path.join(out, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(src, rel), dst)
    with open(os.path.join(out, '_Sidebar.md'), 'w', encoding='utf-8') as fp:
        fp.write(sidebar(src, md_files, names, titles))
    with open(os.path.join(out, '_Footer.md'), 'w', encoding='utf-8') as fp:
        fp.write(FOOTER)

    print(f'페이지 {len(md_files)}개 · 파일 {len(assets)}개')


if __name__ == '__main__':
    main()
