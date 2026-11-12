#!/usr/bin/env python3
"""k3s(containerd)에 쌓인 배포 이미지 중 지울 것을 고른다(#1267). Python 표준 라이브러리만 쓴다.

deploy.sh 의 docker 정리(#318)와 같은 기준으로 저장소마다 만든 시각이 최신인 3개만 남긴다.
k3s 가 쓰는 이미지(Pod · Deployment · CronJob · Job)와 이번 배포 이미지는 오래돼도 남긴다.
고르기만 하고 지우지는 않는다. deploy.sh 가 출력한 ID 를 crictl rmi 로 지운다.

사용: images.py stale <crictl inspecti -o json 결과> <kubectl get ... -A -o json 결과> <이미지 참조...>
"""
import json
import sys

KEEP = 3


def repository(ref):
    """ghcr.io/x/y@sha256:… · ghcr.io/x/y:태그 에서 저장소 이름만 남긴다. 레지스트리 포트(host:5000/x)는 남긴다"""
    name = ref.split('@')[0]
    last_slash = name.rfind('/')
    colon = name.rfind(':')
    if colon > last_slash:
        return name[:colon]
    return name


def images_in_use(cluster):
    """kubectl get -o json 결과의 모든 image · imageID 값을 모은다. 컨테이너 종류(init 포함)와 오브젝트 종류를 가리지 않는다"""
    found = set()
    pending = [cluster]
    while pending:
        node = pending.pop()
        if isinstance(node, list):
            pending.extend(node)
            continue
        if not isinstance(node, dict):
            continue
        for key, value in node.items():
            if key in ('image', 'imageID') and isinstance(value, str):
                found.add(value)
            else:
                pending.append(value)
    return found


def stale_images(inspected, repos, in_use, keep=KEEP):
    """저장소마다 만든 시각이 최신인 keep 개를 남기고, 나머지 중 쓰이지 않는 이미지 ID 를 최신 순으로 낸다"""
    by_repo = {repo: [] for repo in repos}
    for item in inspected:
        status = item['status']
        refs = status.get('repoDigests', []) + status.get('repoTags', [])
        repo = next((repository(ref) for ref in refs if repository(ref) in by_repo), None)
        if repo is None:
            continue
        created = item.get('info', {}).get('imageSpec', {}).get('created', '')
        by_repo[repo].append((created, status['id'], refs))
    stale = []
    for repo in repos:
        newest_first = sorted(by_repo[repo], reverse=True)
        for _, image_id, refs in newest_first[keep:]:
            if in_use.isdisjoint(refs):
                stale.append(image_id)
    return stale


def main(argv):
    if len(argv) < 5 or argv[1] != 'stale':
        raise ValueError('사용: images.py stale <inspecti.json> <cluster.json> <이미지 참조...>')
    with open(argv[2]) as file:
        inspected = json.load(file)
    # 이미지가 하나면 crictl inspecti 는 목록이 아니라 객체 하나를 낸다
    if isinstance(inspected, dict):
        inspected = [inspected]
    with open(argv[3]) as file:
        in_use = images_in_use(json.load(file))
    refs = argv[4:]
    in_use.update(refs)
    repos = list(dict.fromkeys(repository(ref) for ref in refs))
    for image_id in stale_images(inspected, repos, in_use):
        print(image_id)


if __name__ == '__main__':
    try:
        main(sys.argv)
    except Exception as error:
        print('images.py 실패: ' + type(error).__name__ + ': ' + str(error), file=sys.stderr)
        sys.exit(1)
