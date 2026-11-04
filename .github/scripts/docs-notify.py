#!/usr/bin/env python3
"""docs/ 변경을 Slack에 알린다. 파일마다 바뀐 줄 수 · diff 링크 · 바뀐 절 제목을 적는다.

Actions 러너에서 저장소를 checkout한 뒤 실행한다. 절 제목은 checkout한 파일에서 찾는다.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.request

MAX_HEADINGS = 3
# Slack 한 칸은 3000자까지라 파일이 많으면 앞의 것만 적는다
MAX_FILES = 15
HUNK = re.compile(r'^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@')
HEADING = re.compile(r'^#{1,6}\s+(.+?)\s*#*\s*$')


def gh_api(path):
    # 실패하면 None을 돌려줘 알림은 보낼 수 있는 만큼 보낸다
    result = subprocess.run(['gh', 'api', path], capture_output=True, text=True)
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def changed_lines(patch):
    """바뀐 줄의 새 파일 기준 줄 번호. 지우기만 한 곳은 그 자리의 줄 번호를 쓴다."""
    lines = []
    new_line = 0
    for row in patch.split('\n'):
        hunk = HUNK.match(row)
        if hunk:
            new_line = int(hunk.group(1))
            continue
        if row.startswith('+'):
            lines.append(new_line)
            new_line += 1
        elif row.startswith('-'):
            lines.append(new_line)
        else:
            new_line += 1
    return lines


def headings_by_line(path):
    """줄 번호마다 그 줄 위의 가장 가까운 제목. 코드 블록 안의 #은 제목이 아니다."""
    if not os.path.isfile(path):
        return []
    current = None
    in_code = False
    result = []
    with open(path, encoding='utf-8') as file:
        for line in file:
            if line.startswith('```'):
                in_code = not in_code
            elif not in_code:
                heading = HEADING.match(line)
                if heading:
                    current = heading.group(1)
            result.append(current)
    return result


def changed_sections(path, patch):
    headings = headings_by_line(path)
    sections = []
    for line in changed_lines(patch):
        index = min(max(line, 1), len(headings)) - 1
        if index < 0 or headings[index] is None:
            continue
        if headings[index] not in sections:
            sections.append(headings[index])
    return sections


def esc(text):
    return text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def file_line(file, link_base):
    path = file['filename']
    anchor = hashlib.sha256(path.encode()).hexdigest()
    counts = f"+{file['additions']} −{file['deletions']}"
    line = f"• <{link_base}#diff-{anchor}|{esc(path)}> ({file['status']}, {counts})"
    # 새 파일은 전부 바뀐 것이라 절 제목을 적지 않는다. diff가 너무 크면 GitHub이 patch를 주지 않는다
    if file['status'] in ('added', 'removed') or not file.get('patch'):
        return line
    sections = changed_sections(path, file['patch'])
    if sections:
        line += ' — ' + ' · '.join(f'「{esc(s)}」' for s in sections[:MAX_HEADINGS])
    if len(sections) > MAX_HEADINGS:
        line += f' 외 {len(sections) - MAX_HEADINGS}곳'
    return line


def main():
    env = os.environ
    repo, sha = env['REPO'], env['SHA']

    pr = None
    pulls = gh_api(f'repos/{repo}/commits/{sha}/pulls') or []
    for candidate in pulls:
        if candidate.get('merged_at'):
            pr = candidate
            break
    if pr:
        title = f"#{pr['number']} {pr['title']}"
        url = pr['html_url']
        link_base = url + '/files'
        author = pr['user']['login']
    else:
        title = env.get('COMMIT_MSG', '').split('\n')[0]
        url = env['COMPARE_URL']
        link_base = url
        author = env['ACTOR']

    compare = gh_api(f"repos/{repo}/compare/{env['BEFORE']}...{sha}") or {}
    files = [f for f in compare.get('files', [])
             if f['filename'].startswith('docs/') and '/images/' not in f['filename']]
    lines = [file_line(f, link_base) for f in files[:MAX_FILES]]
    if len(files) > MAX_FILES:
        lines.append(f'• 외 {len(files) - MAX_FILES}개 파일')
    if not lines:
        lines = ['• 그림만 바뀌었거나 목록을 가져오지 못했다']

    try:
        names = json.loads(env.get('NAME_MAP') or '{}')
    except ValueError:
        names = {}
    meta = f"📄 문서 변경 · {names.get(author, author)} · `{env['REF']}`"

    text = f'*<{url}|{esc(title)}>*\n' + '\n'.join(lines)
    payload = {'attachments': [{
        'color': '#0969da',
        'fallback': f'{meta} | {title}',
        'blocks': [
            {'type': 'section', 'text': {'type': 'mrkdwn', 'text': text}},
            {'type': 'context', 'elements': [{'type': 'mrkdwn', 'text': esc(meta)}]},
        ],
    }]}
    if env.get('DRY_RUN'):
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    request = urllib.request.Request(
        env['SLACK_WEBHOOK_URL'], data=json.dumps(payload).encode(),
        headers={'Content-Type': 'application/json'})
    urllib.request.urlopen(request, timeout=10)


if __name__ == '__main__':
    sys.exit(main())
