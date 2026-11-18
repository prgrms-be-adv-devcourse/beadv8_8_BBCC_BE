#!/usr/bin/env python3
"""Read Kubernetes rollout and tracing wiring state without modifying resources."""

import argparse
from ipaddress import IPv4Address, IPv4Network
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import urlsplit

AGENT = '/opt/kidly-tracing/opentelemetry-javaagent.jar'
SERVICES = ('gateway', 'order', 'search', 'payment', 'chatbot', 'product', 'member',
            'recommend', 'community', 'notification', 'settlement')
COLLECTOR = 'http://tracing-alloy.kidly-dev.svc:4318/v1/traces'
PRIVATE_NETWORKS = tuple(map(IPv4Network, ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))


def private_ipv4(host):
    try:
        address = IPv4Address(host)
        return any(address in network for network in PRIVATE_NETWORKS)
    except ValueError:
        return False


def private_endpoint(url):
    try:
        parsed = urlsplit(url)
        return (parsed.scheme == 'https' and parsed.port == 30438
                and private_ipv4(parsed.hostname) and not parsed.username
                and not parsed.path and not parsed.query and not parsed.fragment)
    except ValueError:
        return False


def private_receiver_policy(policy):
    spec = policy.get('spec', {})
    if spec.get('podSelector') != {'matchLabels': {'app': 'tempo'}}:
        return False
    try:
        rule, = spec['ingress']
        sender, = rule['from']
        port, = rule['ports']
        network = IPv4Network(sender['ipBlock']['cidr'])
        return (set(sender) == {'ipBlock'} and network.prefixlen == 32
                and private_ipv4(network.network_address)
                and port == {'protocol': 'TCP', 'port': 4318})
    except (KeyError, TypeError, ValueError):
        return False


def get(namespace, kind, name):
    try:
        output = subprocess.check_output(
            ['kubectl', '-n', namespace, 'get', kind, name, '--ignore-not-found', '-o', 'json'],
            text=True, stderr=subprocess.PIPE,
        )
        return json.loads(output) if output.strip() else {}
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise RuntimeError(f'Kubernetes read failed for {namespace}/{kind}/{name}') from error


def ready(deployment):
    desired = deployment.get('spec', {}).get('replicas', 1)
    status = deployment.get('status', {})
    return (desired > 0
            and status.get('observedGeneration', 0) >= deployment.get('metadata', {}).get('generation', 1)
            and all(status.get(key, 0) == desired for key in
                    ('replicas', 'updatedReplicas', 'readyReplicas', 'availableReplicas')))


def pod_spec(deployment):
    return deployment.get('spec', {}).get('template', {}).get('spec', {})


def owns_volume(pod, name):
    return any(volume.get('name') == name for volume in pod.get('volumes', []))


def agent_matches(deployment, name, applied):
    pod = pod_spec(deployment)
    container = next((c for c in pod.get('containers', []) if c.get('name') == name), None)
    if container is None:
        return False
    env = {entry['name']: entry.get('value') or '' for entry in container.get('env', [])}
    mounted = any(
        mount.get('name') == 'kidly-tracing-agent' and mount.get('mountPath') == AGENT
        and mount.get('readOnly') is True
        for mount in container.get('volumeMounts', [])
    )
    volume = owns_volume(pod, 'kidly-tracing-agent')
    java_opts = env.get('JAVA_TOOL_OPTIONS', '').split()
    if applied:
        return (mounted and volume and f'-javaagent:{AGENT}' in java_opts
                and env.get('OTEL_EXPORTER_OTLP_TRACES_ENDPOINT') == COLLECTOR
                and env.get('OTEL_SERVICE_NAME') == f'kidly-{name}')
    return (not volume and not any(m.get('name') == 'kidly-tracing-agent' or m.get('mountPath') == AGENT
                                   for m in container.get('volumeMounts', []))
            and not any(option.startswith(f'-javaagent:{AGENT}') for option in java_opts)
            and env.get('OTEL_EXPORTER_OTLP_TRACES_ENDPOINT') != COLLECTOR
            and env.get('OTEL_SERVICE_NAME') != f'kidly-{name}')


def app_checks(read, applied, services=('gateway', 'order')):
    namespace = 'kidly-dev'
    for name in services:
        deployment = read(namespace, 'deployment', name)
        if not applied and not deployment and name not in ('gateway', 'order'):
            continue
        yield f'{name} rollout', ready(deployment)
        yield f'{name} Agent wiring', agent_matches(deployment, name, applied)
    collector = read(namespace, 'deployment', 'tracing-alloy')
    yield 'tracing Alloy deployment', ready(collector) if applied else not collector
    service = read(namespace, 'service', 'tracing-alloy')
    if applied:
        endpoint = read(namespace, 'configmap', 'tracing-tempo-endpoint')
        yield 'tracing Alloy receiver', (
            service.get('spec', {}).get('type') == 'ClusterIP'
            and service.get('spec', {}).get('selector', {}).get('app') == 'tracing-alloy'
            and any(port.get('port') == 4318 for port in service.get('spec', {}).get('ports', []))
        )
        url = endpoint.get('data', {}).get('url', '')
        yield 'Tempo forwarding configuration', private_endpoint(url)
    else:
        yield 'tracing Alloy service removed', not service


def monitoring_checks(read, applied):
    namespace = 'monitoring'
    tempo = read(namespace, 'deployment', 'tempo')
    yield 'Grafana rollout', ready(read(namespace, 'deployment', 'grafana'))
    yield 'Tempo rollout', ready(tempo)
    yield 'Tempo receiver TLS volume', owns_volume(pod_spec(tempo), 'receiver-tls') == applied
    service = read(namespace, 'service', 'tempo-otlp-private')
    policy = read(namespace, 'networkpolicy', 'tempo-otlp-private')
    if applied:
        spec = service.get('spec', {})
        yield 'private Tempo service', (
            spec.get('type') == 'NodePort' and spec.get('externalTrafficPolicy') == 'Local'
            and spec.get('selector', {}).get('app') == 'tempo'
            and any(port.get('nodePort') == 30438 and port.get('targetPort') == 4318
                    for port in spec.get('ports', []))
        )
        yield 'private Tempo network policy', private_receiver_policy(policy)
    else:
        yield 'private Tempo service removed', not service
        yield 'private Tempo network policy removed', not policy


def selected_services(app_root):
    operations = app_root / 'dev' / 'operations' / 'tracing-agent'
    markers = {path.stem for path in operations.glob('*.enabled')}
    if not markers.issubset(SERVICES):
        raise RuntimeError('Unknown Agent selection file')
    return tuple(name for name in SERVICES if name in markers or name in ('gateway', 'order'))


def read_pods(namespace, name):
    try:
        output = subprocess.check_output(
            ['kubectl', '-n', namespace, 'get', 'pods', '-l', f'app={name}', '-o', 'json'],
            text=True, stderr=subprocess.PIPE,
        )
        return json.loads(output)['items']
    except (OSError, subprocess.CalledProcessError, ValueError, KeyError) as error:
        raise RuntimeError(f'Kubernetes pod read failed for {namespace}/{name}') from error


def pod_status(namespace, name, read=read_pods):
    pods = [pod for pod in read(namespace, name)
            if not pod.get('metadata', {}).get('deletionTimestamp')]
    statuses = [status for pod in pods for status in pod.get('status', {}).get('containerStatuses', [])]
    healthy = bool(pods) and all(
        pod.get('status', {}).get('phase') == 'Running'
        and any(condition.get('type') == 'Ready' and condition.get('status') == 'True'
                for condition in pod.get('status', {}).get('conditions', []))
        for pod in pods
    )
    restarts = sum(status.get('restartCount', 0) for status in statuses)
    previous_oom = sum(status.get('lastState', {}).get('terminated', {}).get('reason') == 'OOMKilled'
                       for status in statuses)
    # Report counters for current Pods, not a new-OOM alert or a lifetime restart total.
    return healthy, f'{name} current Pods: count={len(pods)}, restarts={restarts}, previousOOM={previous_oom}'


def selection_checks(role, app_root):
    if role == 'app':
        operations = app_root / 'dev' / 'operations' / 'tracing-agent'
        yield 'Agent selection files removed', not any(operations.glob('*.enabled'))
        for service in SERVICES:
            yield f'{service} Agent selection removed', not (operations / f'{service}.enabled').exists()
    else:
        marker = app_root / 'monitoring' / 'operations' / 'tempo-private-receiver.enabled'
        yield 'private receiver selection removed', not marker.exists()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('role', choices=('app', 'monitoring'))
    parser.add_argument('state', choices=('applied', 'rolled-back'))
    args = parser.parse_args()
    os.environ['KUBECONFIG'] = str(Path.home() / '.kube' / 'config')
    applied = args.state == 'applied'
    reports = []
    try:
        services = selected_services(Path('/opt/app')) if args.role == 'app' and applied else SERVICES
        results = list(app_checks(get, applied, services) if args.role == 'app'
                       else monitoring_checks(get, applied))
        if applied:
            namespace = 'kidly-dev' if args.role == 'app' else 'monitoring'
            workloads = (*services, 'tracing-alloy') if args.role == 'app' else ('grafana', 'tempo')
            for name in workloads:
                healthy, report = pod_status(namespace, name)
                results.append((f'{name} current Pods ready', healthy))
                reports.append(report)
    except RuntimeError as error:
        print(f'{error}: CHECK REQUIRED')
        return 1
    if args.state == 'rolled-back':
        results.extend(selection_checks(args.role, Path('/opt/app')))
    for description, passed in results:
        print(f'{description}: {"OK" if passed else "CHECK REQUIRED"}')
    for report in reports:
        print(report)
    return 0 if all(passed for _, passed in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
