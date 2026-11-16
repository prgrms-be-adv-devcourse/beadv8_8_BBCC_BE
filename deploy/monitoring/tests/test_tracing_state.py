import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


OPERATIONS = Path(__file__).resolve().parents[2] / 'tracing-ops'


def load(name):
    spec = importlib.util.spec_from_file_location(name, OPERATIONS / f'{name}.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


state = load('verify-state')
route = load('private-route')


def deployment(name, applied=False):
    container = {'name': name}
    pod = {'containers': [container]}
    if applied:
        pod['volumes'] = [{'name': 'kidly-tracing-agent'}]
        container['volumeMounts'] = [
            {'name': 'kidly-tracing-agent', 'mountPath': state.AGENT, 'readOnly': True},
        ]
        container['env'] = [
            {'name': 'JAVA_TOOL_OPTIONS', 'value': f'-Xmx256m -javaagent:{state.AGENT}'},
            {'name': 'OTEL_EXPORTER_OTLP_TRACES_ENDPOINT', 'value': state.COLLECTOR},
            {'name': 'OTEL_SERVICE_NAME', 'value': f'kidly-{name}'},
        ]
    return {
        'metadata': {'generation': 2},
        'spec': {'replicas': 1, 'template': {'spec': pod}},
        'status': {'observedGeneration': 2, 'replicas': 1, 'updatedReplicas': 1,
                   'readyReplicas': 1, 'availableReplicas': 1},
    }


class TracingVerificationTests(unittest.TestCase):
    def test_applied_and_rolled_back_app(self):
        resources = {('deployment', name): deployment(name, applied=True) for name in ('gateway', 'order')}
        resources[('deployment', 'tracing-alloy')] = deployment('alloy')
        resources[('service', 'tracing-alloy')] = {
            'spec': {'type': 'ClusterIP', 'selector': {'app': 'tracing-alloy'}, 'ports': [{'port': 4318}]},
        }
        resources[('configmap', 'tracing-tempo-endpoint')] = route.app_resources('10.1.2.3')[0]
        read = lambda namespace, kind, name: resources.get((kind, name), {})
        self.assertTrue(all(dict(state.app_checks(read, True)).values()))
        self.assertFalse(all(dict(state.app_checks(read, False)).values()))
        resources[('deployment', 'gateway')]['status']['observedGeneration'] = 1
        self.assertFalse(dict(state.app_checks(read, True))['gateway rollout'])
        resources = {('deployment', name): deployment(name) for name in ('gateway', 'order')}
        self.assertTrue(all(dict(state.app_checks(read, False)).values()))

    def test_monitoring_checks_detect_incomplete_rollback_and_broad_policy(self):
        tempo = deployment('tempo')
        tempo_pod = state.pod_spec(tempo)
        tempo_pod['volumes'] = [{'name': 'receiver-tls'}]
        service, policy = route.monitoring_resources('10.2.3.4')
        resources = {('deployment', 'grafana'): deployment('grafana'), ('deployment', 'tempo'): tempo,
                     ('service', 'tempo-otlp-private'): service, ('networkpolicy', 'tempo-otlp-private'): policy}
        read = lambda namespace, kind, name: resources.get((kind, name), {})
        self.assertTrue(all(dict(state.monitoring_checks(read, True)).values()))
        policy['spec']['ingress'][0]['from'][0]['ipBlock']['cidr'] = '10.0.0.0/8'
        self.assertFalse(dict(state.monitoring_checks(read, True))['private Tempo network policy'])
        del resources[('networkpolicy', 'tempo-otlp-private')]
        del tempo_pod['volumes']
        self.assertFalse(dict(state.monitoring_checks(read, False))['private Tempo service removed'])
        del resources[('service', 'tempo-otlp-private')]
        self.assertTrue(all(dict(state.monitoring_checks(read, False)).values()))

    def test_endpoint_rejects_nonprivate_or_malformed_values(self):
        for url in ('http://10.2.3.4:30438', 'https://8.8.8.8:30438', 'https://10.2.3.4:abc',
                    'https://10.2.3.4:30438/path', 'https://user@10.2.3.4:30438'):
            with self.subTest(url=url):
                self.assertFalse(state.private_endpoint(url))

    def test_rollback_allows_unrelated_otel_settings(self):
        app = deployment('gateway')
        state.pod_spec(app)['containers'][0]['env'] = [
            {'name': 'OTEL_SERVICE_NAME', 'value': 'another-operation'},
        ]
        self.assertTrue(state.agent_matches(app, 'gateway', False))

    def test_rollback_rejects_selection_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for role, marker in (
                    ('app', root / 'dev/operations/tracing-agent/gateway.enabled'),
                    ('monitoring', root / 'monitoring/operations/tempo-private-receiver.enabled')):
                with self.subTest(role=role):
                    self.assertTrue(all(dict(state.selection_checks(role, root)).values()))
                    marker.parent.mkdir(parents=True)
                    marker.touch()
                    self.assertFalse(all(dict(state.selection_checks(role, root)).values()))

    def test_kubernetes_read_failure_reports_only_requested_resource(self):
        error = subprocess.CalledProcessError(1, ['kubectl'], stderr='fixture private stderr')
        with mock.patch.object(state.subprocess, 'check_output', side_effect=error):
            with self.assertRaises(RuntimeError) as failure:
                state.get('monitoring', 'deployment', 'tempo')
        self.assertEqual(str(failure.exception), 'Kubernetes read failed for monitoring/deployment/tempo')
