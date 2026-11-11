#!/usr/bin/env python3
"""compose 서비스 하나를 t3.large 단독 k3s 의 오브젝트로 바꾼다(#1142). Python 표준 라이브러리만 쓴다.

서비스의 환경 변수 · 이미지 · 메모리 상한 · 마운트는 서버의 docker-compose.yml 과 .env 가 원본이다.
deploy.sh 가 `docker compose config --format json <서비스>` 결과를 stdin 으로 넘기면, kubectl apply 에 넣을 List(JSON)를 낸다.
비밀은 명령줄 · 출력 로그에 남기지 않고 stdout 으로만 kubectl 에 넘어간다.
"""
import base64
import hashlib
import json
import sys
from pathlib import Path

# HPA 로 늘리는 서비스: (최소, 최대, CPU 사용률 %). 추천은 구독 토픽이 파티션 3 이라 4번째 Pod 부터는 이벤트를 받지 않는다(#1028)
AUTOSCALE = {'recommend': (1, 3, 70)}
# HPA 기준이 되는 서비스만 CPU request 를 크게 잡는다. 나머지는 노드(vCPU 2)에 9개가 함께 올라가도록 작게 둔다
CPU_REQUEST = {'recommend': '200m'}
DEFAULT_CPU_REQUEST = '50m'
# 두 프로세스가 함께 돌면 안 되는 스케줄러가 있는 서비스. 롤링 배포의 겹치는 시간을 두지 않고 옛 Pod 를 먼저 내린다(compose 때와 같다)
# 상품: 아웃박스 릴레이에 잠금이 없다(domains/product CLAUDE.md). 알림: 재발송 스케줄러가 같은 알림을 두 번 보낼 수 있다
SINGLE_INSTANCE = {'product', 'notification'}
# 이미지의 앱 유저 그룹(Dockerfile 의 groupadd -g 1001)
APP_GID = 1001
# 다른 서비스를 클러스터 안 이름으로 부르는 입구. 다른 서비스가 모두 뜬 뒤 맨 마지막에 올린다
EDGE = {'gateway'}
DEFAULT_PORT = 8080


def rank(name):
    # 배포 순서: 롤링(0) → 옛 프로세스를 먼저 내리는 서비스(1) → 입구(2)
    return 2 if name in EDGE else 1 if name in SINGLE_INSTANCE else 0


LOG_DIR = '/app/logs'


def namespace(env):
    return 'kidly-' + env


def env_vars(service):
    # 값이 없는 키(`- KEY` 이고 .env 에도 없음)는 compose 처럼 넣지 않는다
    return {key: str(value) for key, value in (service.get('environment') or {}).items() if value is not None}


def mebibytes(mem_limit):
    value = int(mem_limit)
    if value <= 0 or value % (1024 * 1024):
        raise ValueError('mem_limit 은 MiB 단위여야 합니다: %s' % mem_limit)
    return value // (1024 * 1024)


def container_port(service):
    targets = [int(item['target']) for item in service.get('ports') or []]
    return targets[0] if targets else DEFAULT_PORT


def probes(service):
    """Spring 서비스(healthcheck 가 /actuator/health)는 health 그룹을, 그 밖(kafka-exporter 등)은 포트 연결을 본다"""
    def http(path, period, failures):
        return {'httpGet': {'path': path, 'port': 'http'}, 'periodSeconds': period, 'failureThreshold': failures}

    test = ' '.join(str(part) for part in (service.get('healthcheck') or {}).get('test') or [])
    if '/actuator/health' in test:
        # 쿠버네티스 위에서는 Spring Boot 가 liveness · readiness health 그룹을 켠다. 기동은 5분까지 기다린다(compose start_period 180s)
        return {'startupProbe': http('/actuator/health/liveness', 5, 60),
                'readinessProbe': http('/actuator/health/readiness', 10, 3),
                'livenessProbe': http('/actuator/health/liveness', 10, 6)}
    tcp = {'tcpSocket': {'port': 'http'}}
    return {'startupProbe': dict(tcp, periodSeconds=5, failureThreshold=24),
            'readinessProbe': dict(tcp, periodSeconds=10, failureThreshold=3),
            'livenessProbe': dict(tcp, periodSeconds=20, failureThreshold=3)}


def security_context(service):
    """compose 의 user · read_only · cap_drop · no-new-privileges 를 그대로 옮긴다"""
    context = {}
    if service.get('user'):
        user, _, group = str(service['user']).partition(':')
        context.update(runAsUser=int(user), runAsNonRoot=int(user) != 0)
        if group:
            context['runAsGroup'] = int(group)
    if service.get('read_only'):
        context['readOnlyRootFilesystem'] = True
    if 'ALL' in (service.get('cap_drop') or []):
        context['capabilities'] = {'drop': ['ALL']}
    if any(str(option).startswith('no-new-privileges') for option in service.get('security_opt') or []):
        context['allowPrivilegeEscalation'] = False
    return context


def secret_mounts(service, project_dir):
    """compose 가 읽기 전용으로 붙이는 jwt 키(./jwt 폴더 또는 그 안의 파일)를 Secret 마운트로 바꾼다. 로그 폴더는 emptyDir 로 둔다"""
    files, mounts = {}, []
    for volume in service.get('volumes') or []:
        if volume.get('type') != 'bind' or volume['target'] == LOG_DIR:
            continue
        source = Path(volume['source'])
        if source.parent != project_dir / 'jwt' and source != project_dir / 'jwt':
            raise ValueError('k3s 로 옮길 수 없는 마운트입니다: ' + volume['target'])
        if source.is_dir():
            for item in sorted(source.iterdir()):
                files[item.name] = item.read_bytes()
            mounts.append({'name': 'jwt', 'mountPath': volume['target'], 'readOnly': True})
        else:
            files[source.name] = source.read_bytes()
            mounts.append({'name': 'jwt', 'mountPath': volume['target'], 'subPath': source.name, 'readOnly': True})
    return files, mounts


def manifest(env, name, node_port, job, service, project_dir):
    ns = namespace(env)
    labels = {'app': name}
    # monitoring-agent Alloy 가 이 라벨을 Prometheus job · Loki service_name 으로 쓴다(compose 때와 같은 이름)
    pod_labels = dict(labels, **{'kidly/job': job})
    listen = container_port(service)
    port = {'name': 'http', 'port': listen, 'targetPort': 'http'}
    variables = env_vars(service)
    memory = '%dMi' % mebibytes(service['mem_limit'])
    jwt_files, jwt_mounts = secret_mounts(service, project_dir)
    # 환경 변수 · 키가 바뀌면 Pod 를 다시 띄운다(같은 이미지로 다시 배포해도 새 값이 들어간다)
    digest = hashlib.sha256(json.dumps([variables, sorted((k, v.hex()) for k, v in jwt_files.items())],
                                       sort_keys=True).encode()).hexdigest()[:16]

    logs = any(volume.get('target') == LOG_DIR for volume in service.get('volumes') or [])
    volumes = [{'name': 'logs', 'emptyDir': {'sizeLimit': '256Mi'}}] if logs else []
    if jwt_files:
        volumes.append({'name': 'jwt', 'secret': {'secretName': name + '-jwt', 'defaultMode': 0o440}})
    container = {
        'name': name,
        'image': service['image'],
        # 노드에 이미지가 있으면 쓴다. HPA 가 늘린 Pod 는 노드의 이미지를 쓰므로 배포 잡이 끝나 만료된 pull secret 이 문제 되지 않는다
        'imagePullPolicy': 'IfNotPresent',
        'envFrom': [{'secretRef': {'name': name + '-env'}}],
        'ports': [{'name': 'http', 'containerPort': listen}],
        # Service 에서 빠지는 동안 들어온 요청을 받도록 잠깐 기다린 뒤 종료를 시작한다
        'lifecycle': {'preStop': {'exec': {'command': ['sleep', '5']}}},
        # 메모리는 compose mem_limit 을 그대로 request · limit 으로 쓴다. CPU limit 은 두지 않는다(기동 때 막히면 기동이 길어진다)
        'resources': {'requests': {'cpu': CPU_REQUEST.get(name, DEFAULT_CPU_REQUEST), 'memory': memory},
                      'limits': {'memory': memory}},
        'volumeMounts': ([{'name': 'logs', 'mountPath': LOG_DIR}] if logs else []) + jwt_mounts,
    }
    container.update(probes(service))
    # compose 의 command 는 이미지의 CMD 를 바꾼다. 쿠버네티스에서는 args 가 같은 일을 한다
    if service.get('command'):
        container['args'] = [str(part) for part in service['command']]
    if security_context(service):
        container['securityContext'] = security_context(service)
    items = [
        {'apiVersion': 'v1', 'kind': 'Secret', 'metadata': {'name': name + '-env', 'namespace': ns},
         'type': 'Opaque', 'stringData': variables},
        {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'metadata': {'name': name, 'namespace': ns, 'labels': labels},
         'spec': {
             # replicas 를 두지 않는다. HPA 나 dev 끄기(0)가 정한 수를 배포가 되돌리지 않게 한다(처음 만들 때는 1)
             'revisionHistoryLimit': 3,
             # 새 Pod 가 준비된 뒤 옛 Pod 를 내린다. 배포 중에도 요청을 받는다. SINGLE_INSTANCE 는 옛 Pod 를 먼저 내린다
             'strategy': {'type': 'Recreate'} if name in SINGLE_INSTANCE else
                         {'type': 'RollingUpdate', 'rollingUpdate': {'maxSurge': 1, 'maxUnavailable': 0}},
             'selector': {'matchLabels': labels},
             'template': {
                 'metadata': {'labels': pod_labels, 'annotations': {'kidly/config-hash': digest}},
                 'spec': {
                     'imagePullSecrets': [{'name': 'ghcr-pull'}],
                     # 앱은 UID/GID 1001(Dockerfile)로 돈다. Secret 파일(0440, root 소유)을 읽을 수 있게 그룹을 1001 로 붙인다
                     'securityContext': {'fsGroup': APP_GID},
                     # 처리 중인 요청 · Kafka 메시지를 마치고 내려간다(Spring graceful shutdown 기본 30초 + preStop 5초)
                     'terminationGracePeriodSeconds': 45,
                     'containers': [container],
                     # 파일 로그는 Pod 와 함께 사라진다. 로그는 monitoring-agent Alloy 가 Pod 콘솔에서 Loki 로 모은다
                     # 다른 서비스는 같은 namespace 의 Service 이름(<이름>:8080)으로 부른다. 서비스 링크 환경 변수는 끈다
                     'enableServiceLinks': False,
                     'volumes': volumes,
                 }}}},
        # NodePort 가 있으면 이 서버(compose 의 Gateway · nginx)가 host 주소로 붙는다. 보안 그룹에서 밖으로 열지 않는다(X-Member-* 위조 방지)
        # 없으면 클러스터 안에서만 <이름>:8080 으로 닿는다
        {'apiVersion': 'v1', 'kind': 'Service', 'metadata': {'name': name, 'namespace': ns, 'labels': labels},
         'spec': dict({'type': 'NodePort', 'ports': [dict(port, nodePort=node_port)]} if node_port else
                      {'type': 'ClusterIP', 'ports': [port]}, selector=labels)},
    ]
    if jwt_files:
        items.insert(1, {'apiVersion': 'v1', 'kind': 'Secret', 'metadata': {'name': name + '-jwt', 'namespace': ns},
                         'type': 'Opaque',
                         'data': {key: base64.b64encode(value).decode() for key, value in jwt_files.items()}})
    if name in AUTOSCALE:
        low, high, cpu = AUTOSCALE[name]
        items.append({'apiVersion': 'autoscaling/v2', 'kind': 'HorizontalPodAutoscaler',
                      'metadata': {'name': name, 'namespace': ns, 'labels': labels},
                      'spec': {'scaleTargetRef': {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'name': name},
                               'minReplicas': low, 'maxReplicas': high,
                               'metrics': [{'type': 'Resource', 'resource': {
                                   'name': 'cpu', 'target': {'type': 'Utilization', 'averageUtilization': cpu}}}],
                               # 줄일 때는 5분 동안 낮게 유지될 때만 1분에 하나씩 줄인다(Kafka 리밸런스를 줄인다)
                               'behavior': {'scaleDown': {'stabilizationWindowSeconds': 300,
                                                          'policies': [{'type': 'Pods', 'value': 1, 'periodSeconds': 60}]}}}})
    return {'apiVersion': 'v1', 'kind': 'List', 'items': items}


def pull_secret(env, docker_config):
    """배포 잡이 이 세션에만 로그인한 DOCKER_CONFIG 의 GHCR 자격으로 pull secret 을 만든다. 서버에 장기 토큰을 두지 않는다(#281)"""
    auths = json.loads((Path(docker_config) / 'config.json').read_text()).get('auths', {})
    if not auths.get('ghcr.io', {}).get('auth'):
        raise ValueError('DOCKER_CONFIG 에 ghcr.io 로그인 정보가 없습니다')
    payload = json.dumps({'auths': {'ghcr.io': {'auth': auths['ghcr.io']['auth']}}})
    return {'apiVersion': 'v1', 'kind': 'Secret', 'type': 'kubernetes.io/dockerconfigjson',
            'metadata': {'name': 'ghcr-pull', 'namespace': namespace(env)},
            'data': {'.dockerconfigjson': base64.b64encode(payload.encode()).decode()}}


def main(argv):
    command, env = argv[1], argv[2]
    if env not in ('dev', 'prod'):
        raise ValueError('환경은 dev 또는 prod 여야 합니다')
    if command == 'namespace':
        print(namespace(env))
    elif command == 'rank':
        print(rank(argv[3]))
    elif command == 'single-instance':
        # 종료 코드로 답한다: 0 이면 옛 프로세스를 먼저 내려야 하는 서비스
        sys.exit(0 if argv[3] in SINGLE_INSTANCE else 1)
    elif command == 'pull-secret':
        print(json.dumps(pull_secret(env, argv[3])))
    elif command == 'manifest':
        compose_service, name, job = argv[3], argv[4], argv[6]
        node_port = None if argv[5] == '-' else int(argv[5])
        if node_port is not None and not 30000 <= node_port <= 32767:
            raise ValueError('NodePort 는 30000~32767 이어야 합니다')
        service = json.load(sys.stdin)['services'][compose_service]
        print(json.dumps(manifest(env, name, node_port, job, service, Path.cwd())))
    else:
        raise ValueError('지원하지 않는 명령입니다: ' + command)


if __name__ == '__main__':
    try:
        main(sys.argv)
    except Exception as error:
        # 비밀 값이 섞일 수 있는 입력은 출력하지 않는다
        print('k3s.py 실패: ' + type(error).__name__ + ': ' + str(error), file=sys.stderr)
        sys.exit(1)
