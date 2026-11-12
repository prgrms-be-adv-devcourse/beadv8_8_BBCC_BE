#!/usr/bin/env python3
"""Environment lifecycle and independent monitoring inventory; Python stdlib only."""
import contextlib
import fcntl
import json
import os
import shlex
from pathlib import Path
import subprocess
import sys
import tempfile
import time

ROOT = Path(os.environ.get('KIDLY_APP_ROOT', '/opt/app'))
STATE = ROOT / 'monitoring' / 'operations'
SERVICES = {
    'search-service': ('kidly-search', 'SEARCH_PORT', 'search'),
    'gateway': ('kidly-gateway', 'GATEWAY_PORT', 'gateway'),
    'recommend-service': ('kidly-recommend', 'RECOMMEND_PORT', 'recommend'),
    'settlement-service': ('kidly-settlement', 'SETTLEMENT_PORT', 'settlement'),
    'member-service': ('kidly-member', 'MEMBER_PORT', 'member'),
    'payment-service': ('kidly-payment', 'PAYMENT_PORT', 'payment'),
    'product-service': ('kidly-product', 'PRODUCT_PORT', 'product'),
    'notification-service': ('kidly-notification', 'NOTIFICATION_PORT', 'notification'),
    'order-service': ('kidly-order', 'ORDER_PORT', 'order'),
    'community-service': ('kidly-community', 'COMMUNITY_PORT', 'community'),
}
# compose 밖 k3s(t3.large 에 단독으로 띄운 k3s, deploy/k3s)에서 도는 서비스. .env 의 K3S_PROFILES 에 있는 profile 이다(#1142)
# 한 profile 은 COMPOSE_PROFILES · K3S_PROFILES 중 한쪽에만 둔다. <PROFILE>_NODE_PORT 가 있으면 이 서버에서 그 NodePort 로 닿는다
# 메트릭은 monitoring-agent Alloy 가 k3s API 로 Pod 를 찾아 모으므로 대상 파일을 쓰지 않고, 기대 목록에만 넣는다
KUBECONFIG = Path.home() / '.kube' / 'config'
# 앱 서비스(SERVICES) 밖에서 k3s 로 옮길 수 있는 compose 서비스: profile → (compose 서비스, Deployment 이름, Prometheus job)
K3S_EXTRAS = {'kafka-monitoring': ('kafka-exporter', 'kafka-exporter', 'kidly-kafka'),
              'redis': ('redis', 'redis', 'kidly-redis')}


def node_port_key(profile):
    return profile.upper() + '_NODE_PORT'


def env_values(env, wanted):
    values = {}
    for line in (ROOT / env / '.env').read_text().splitlines():
        key, sep, value = line.partition('=')
        if sep and wanted(key):
            values[key] = ' '.join(shlex.split(value, comments=True))
    return values


def profile_list(value):
    return {item.strip() for item in value.split(',') if item.strip()}


def validate(data):
    if data.get('env') not in ('dev', 'prod') or type(data.get('enabled')) is not bool:
        raise ValueError('Invalid environment state')
    if data.get('phase') not in ('running', 'stopped', 'stopping', 'starting'):
        raise ValueError('Invalid operation phase')
    if not isinstance(data.get('changed_at'), (int, float)) or data['changed_at'] < 0:
        raise ValueError('Invalid timestamp')
    if 'kafka_exporter_expected' in data and type(data['kafka_exporter_expected']) is not bool:
        raise ValueError('Invalid Kafka exporter intention')
    jobs = data.get('jobs')
    allowed = {entry[0] for entry in SERVICES.values()} | {'kidly'}  # 기존 운영 상태 파일 호환
    if not isinstance(jobs, list) or not jobs or len(jobs) != len(set(jobs)) or not set(jobs) <= allowed:
        raise ValueError('Invalid expected services')
    if data['phase'] == 'running' and not data['enabled'] or data['phase'] == 'stopped' and data['enabled']:
        raise ValueError('Inconsistent environment phase')
    return data


def atomic(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(name, 0o644)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def load(env):
    path = STATE / (env + '.json')
    return validate(json.loads(path.read_text())) if path.exists() else None


def metrics(data):
    validate(data)
    env = data['env']
    lines = [
        'kidly_environment_enabled{env="%s"} %d' % (env, data['enabled']),
        'kidly_environment_changed_timestamp_seconds{env="%s"} %s' % (env, data['changed_at']),
        'kidly_environment_transition_active{env="%s"} %d' % (env, data['phase'] in ('starting', 'stopping')),
    ]
    lines.append('kidly_kafka_exporter_expected{env="%s"} %d' %
                 (env, data['enabled'] and data.get('kafka_exporter_expected', False)))
    lines += ['kidly_app_expected{env="%s",app_job="%s"} %d' % (env, job, data['enabled']) for job in data['jobs']]
    return '\n'.join(lines) + '\n'


def write(data, monitoring=False):
    validate(data)
    # JSON is durable control state. The exporter file is a separately validated atomic projection.
    atomic(STATE / (data['env'] + '.json'), json.dumps(data) + '\n')
    if monitoring:
        atomic(STATE / (data['env'] + '.prom'), metrics(data))
        registered = [validate(json.loads(path.read_text())) for path in STATE.glob('*.json')]
        anchor = 'kidly_monitoring_inventory_present 1\nkidly_monitoring_inventory_initialized 1\n'
        anchor += ''.join('kidly_environment_registered{env="%s"} 1\n' % item['env'] for item in registered)
        atomic(STATE / 'inventory.prom', anchor)


def configuration(env):
    directory = ROOT / env
    result = {}
    for line in (directory / '.env').read_text().splitlines():
        key, sep, value = line.partition('=')
        if sep and key in {'COMPOSE_PROFILES'} | {entry[1] for entry in SERVICES.values()}:
            result[key] = ' '.join(shlex.split(value, comments=True))
    profiles = {item.strip() for item in result.get('COMPOSE_PROFILES', '').split(',')}
    targets = []
    for service, (job, port_key, profile) in SERVICES.items():
        if profile and profile not in profiles:
            continue
        port = result.get(port_key, '')
        if not port.isdigit() or not 0 < int(port) < 65536:
            raise ValueError('Missing or invalid port: ' + port_key)
        targets.append((service, job, int(port)))
    return targets


def k3s_workloads(env):
    """(compose 서비스, profile, Deployment 이름, NodePort 또는 None, job). K3S_PROFILES 에 있는 것, 앱 서비스 먼저"""
    values = env_values(env, lambda key: key in ('COMPOSE_PROFILES', 'K3S_PROFILES') or key.endswith('_NODE_PORT'))
    k3s = profile_list(values.get('K3S_PROFILES', ''))
    both = k3s & profile_list(values.get('COMPOSE_PROFILES', ''))
    if both:
        raise ValueError('Profile in both COMPOSE_PROFILES and K3S_PROFILES: ' + ','.join(sorted(both)))
    known = {profile for _, _, profile in SERVICES.values()} | set(K3S_EXTRAS)
    if k3s - known:
        raise ValueError('Unknown K3S_PROFILES: ' + ','.join(sorted(k3s - known)))
    entries = [(service, profile, profile, job) for service, (job, _, profile) in SERVICES.items()]
    entries += [(service, profile, name, job) for profile, (service, name, job) in K3S_EXTRAS.items()]
    result = []
    for service, profile, name, job in entries:
        if profile not in k3s:
            continue
        port = values.get(node_port_key(profile))
        if port is not None and (not port.isdigit() or not 30000 <= int(port) <= 32767):
            raise ValueError('Invalid NodePort: ' + node_port_key(profile))
        result.append((service, profile, name, int(port) if port is not None else None, job))
    return result


def k3s_services(env):
    """(compose 서비스, job, profile, NodePort 또는 None). k3s 에 뜨는 앱 서비스"""
    return [(service, job, profile, port) for service, profile, _, port, job in k3s_workloads(env) if service in SERVICES]


def expected_jobs(env):
    return [job for _, job, _ in configuration(env)] + [job for _, job, _, _ in k3s_services(env)]


def kafka_expected(env):
    # compose(파일 대상) 또는 k3s(Pod 를 찾음) 어느 쪽이든 kafka-exporter 가 떠야 하는 환경
    return kafka_target(env) is not None or any(profile == 'kafka-monitoring' for _, profile, _, _, _ in k3s_workloads(env))


def kafka_target(env):
    # Read only monitoring keys; never expose application secrets.
    values = {}
    for line in (ROOT / env / '.env').read_text().splitlines():
        key, sep, value = line.partition('=')
        if sep and key in ('COMPOSE_PROFILES', 'KAFKA_EXPORTER_PORT'):
            values[key] = ' '.join(shlex.split(value, comments=True))
    if 'kafka-monitoring' not in {item.strip() for item in values.get('COMPOSE_PROFILES', '').split(',')}:
        return None
    port = values.get('KAFKA_EXPORTER_PORT', '')
    if not port.isdigit() or not 0 < int(port) < 65536:
        raise ValueError('Missing or invalid KAFKA_EXPORTER_PORT')
    return int(port)


def snapshot(env):
    if not (ROOT / env / '.env').exists():
        return None
    data = load(env)
    if not data and not (ROOT / 'monitoring' / 'targets' / (env + '.yml')).exists():
        return None
    jobs = expected_jobs(env)
    if data:
        return dict(data, jobs=jobs, kafka_exporter_expected=kafka_expected(env))
    # Never infer intentional shutdown from a missing container or a failed scrape.
    # Only previously registered environments are adopted automatically.
    if not (ROOT / 'monitoring' / 'targets' / (env + '.yml')).exists():
        return None
    return {'env': env, 'enabled': True, 'phase': 'running', 'changed_at': time.time(), 'jobs': jobs, 'kafka_exporter_expected': kafka_expected(env)}


def register_targets(env):
    root = ROOT / 'monitoring' / 'targets'
    (root / (env + '.yml')).unlink(missing_ok=True)
    configured = configuration(env)
    for service, _, port in configured:
        suffix = SERVICES[service][2]
        atomic(root / suffix / (env + '.yml'), '- targets: ["127.0.0.1:%d"]\n  labels:\n    env: %s\n' % (port, env))
    kafka_port = kafka_target(env)
    if kafka_port is not None:
        atomic(root / 'kafka' / (env + '.yml'),
               '- targets: ["127.0.0.1:%d"]\n  labels:\n    env: %s\n' % (kafka_port, env))
    else:
        (root / 'kafka' / (env + '.yml')).unlink(missing_ok=True)
    active = {service for service, _, _ in configured}
    for service, (_, _, profile) in SERVICES.items():
        if service not in active:
            (root / profile / (env + '.yml')).unlink(missing_ok=True)


def run(command, capture=False):
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL)
    if result.returncode:
        raise RuntimeError('Server command failed; inspect container health (secret values are not printed)')
    return result.stdout.strip() if capture else None


def kubectl(env, *args):
    return ['kubectl', '--kubeconfig', str(KUBECONFIG), '-n', 'kidly-' + env, *args]


def compose_used(env):
    # compose 서비스는 모두 profile 이 있어, COMPOSE_PROFILES 가 비면 띄울 것이 없다(up 은 no service selected 로 실패한다)
    return bool(profile_list(env_values(env, lambda key: key == 'COMPOSE_PROFILES').get('COMPOSE_PROFILES', '')))


def suspend_cronjobs(env, suspend):
    names = run(kubectl(env, 'get', 'cronjob', '-o', 'name'), capture=True)
    payload = json.dumps({'spec': {'suspend': suspend}}, separators=(',', ':'))
    for name in names.splitlines():
        run(kubectl(env, 'patch', name, '--type', 'merge', '-p', payload))


def scale_k3s(env, replicas):
    # HPA 가 있는 서비스도 0 이면 늘리지 않고, 1 로 되돌리면 HPA 가 다시 맡는다
    if not k3s_workloads(env):
        return
    if not replicas:
        suspend_cronjobs(env, True)
    run(kubectl(env, 'scale', 'deployment', '--all', '--replicas', str(replicas)))
    if replicas:
        run(kubectl(env, 'wait', '--for=condition=Available', 'deployment', '--all', '--timeout=600s'))
        suspend_cronjobs(env, False)


def compose(env, *args):
    return ['docker', 'compose', '-p', env, '-f', str(ROOT / env / 'docker-compose.yml'), '--project-directory', str(ROOT / env),
            '--env-file', str(ROOT / env / '.env'), '--env-file', str(ROOT / env / 'image.env'), *args]


@contextlib.contextmanager
def lock(env):
    STATE.mkdir(parents=True, exist_ok=True)
    with (STATE / (env + '.lock')).open('a') as file:
        fcntl.flock(file, fcntl.LOCK_EX)
        yield


def control(env, action):
    with lock('environment-operation'), lock(env):
        data = {'env': env, 'enabled': action == 'resume', 'phase': 'starting' if action == 'resume' else 'stopping',
                'changed_at': time.time(), 'jobs': expected_jobs(env), 'kafka_exporter_expected': kafka_expected(env)}
        if action == 'resume':
            # Monitor a failed startup; do not silently leave the environment disabled.
            write(data)
            # Gateway(compose)가 붙을 k3s 서비스를 먼저 띄운다
            scale_k3s(env, 1)
            if compose_used(env):
                run(compose(env, 'up', '-d', '--wait', '--wait-timeout', '180', '--pull', 'never'))
            register_targets(env)
            data['phase'] = 'running'
        else:
            old = load(env)
            write(data)  # Automatic deployment must see this before containers are stopped.
            try:
                if compose_used(env):
                    run(compose(env, 'stop', '--timeout', '30'))
                scale_k3s(env, 0)
            except Exception:
                write(old or dict(data, enabled=True, phase='running'))
                raise
            root = ROOT / 'monitoring' / 'targets'
            for _, _, profile in SERVICES.values():
                (root / profile / (env + '.yml')).unlink(missing_ok=True)
            (root / 'kafka' / (env + '.yml')).unlink(missing_ok=True)
            data['phase'] = 'stopped'
        write(data)
        return data


def main():
    action, env = sys.argv[1:3]
    if env not in ('dev', 'prod'):
        raise ValueError('Environment must be dev or prod')
    if action == 'mode':
        data = load(env)
        print('stopped' if data and not data['enabled'] else 'running')
    elif action == 'snapshot':
        print(json.dumps(snapshot(env)))
    elif action == 'plan':
        data = {'env': env, 'enabled': True, 'phase': 'running', 'changed_at': time.time(),
                'jobs': expected_jobs(env),
                'kafka_exporter_expected': kafka_expected(env)}
        print(json.dumps(validate(data)))
    elif action == 'targets':
        register_targets(env)
        data = {'env': env, 'enabled': True, 'phase': 'running', 'changed_at': time.time(),
                'jobs': expected_jobs(env),
                'kafka_exporter_expected': kafka_expected(env)}
        write(data)
    elif action == 'inventory':
        data = validate(json.load(sys.stdin))
        if data['env'] != env:
            raise ValueError('State/environment mismatch')
        with lock('inventory'):
            write(data, monitoring=True)
    elif action == 'k3s':
        # deploy.sh 가 읽는다: 한 줄에 compose 서비스 · profile · Deployment 이름 · NodePort(없으면 -) · job
        for service, profile, name, port, job in k3s_workloads(env):
            print(service, profile, name, port or '-', job)
    elif action in ('stop', 'resume'):
        print(json.dumps(control(env, action)))
    else:
        raise ValueError('Unsupported operation')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # No compose output, connection values, tokens, or application logs are printed.
        print('Environment operation failed: ' + type(error).__name__ + ': ' + str(error), file=sys.stderr)
        sys.exit(1)
