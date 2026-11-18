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

    def test_selected_services_include_payment_and_chatbot(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            markers = root / 'dev/operations/tracing-agent'
            markers.mkdir(parents=True)
            for name in ('search', 'payment', 'chatbot'):
                (markers / f'{name}.enabled').touch()
            self.assertEqual(state.selected_services(root),
                             ('gateway', 'order', 'search', 'payment', 'chatbot'))
            (markers / 'unknown.enabled').touch()
            with self.assertRaisesRegex(RuntimeError, '^Unknown Agent selection file$'):
                state.selected_services(root)

    def test_selected_agent_miswiring_is_not_skipped(self):
        resources = {name: deployment(name, applied=True) for name in ('payment', 'chatbot')}
        resources['chatbot']['spec']['template']['spec']['containers'][0]['env'] = []
        read = lambda namespace, kind, name: resources.get(name, {})
        result = dict(state.app_checks(read, True, ('payment', 'chatbot')))
        self.assertTrue(result['payment Agent wiring'])
        self.assertFalse(result['chatbot Agent wiring'])

    def test_pod_report_excludes_retired_pods_and_private_state(self):
        pod = {'status': {'phase': 'Running',
                         'conditions': [{'type': 'Ready', 'status': 'True'}],
                         'containerStatuses': [{'restartCount': 2,
                             'lastState': {'terminated': {'reason': 'OOMKilled',
                                                         'message': 'private diagnostic'}}}]}}
        retired = {'metadata': {'deletionTimestamp': 'fixture'}, 'status': {}}
        ready, report = state.pod_status('monitoring', 'tempo', lambda *args: [pod, retired])
        self.assertTrue(ready)
        self.assertEqual(report, 'tempo current Pods: count=1, restarts=2, previousOOM=1')
        self.assertNotIn('private', report)
        pod['status']['conditions'][0]['status'] = 'False'
        self.assertFalse(state.pod_status('monitoring', 'tempo', lambda *args: [pod])[0])
        self.assertFalse(state.pod_status('monitoring', 'tempo', lambda *args: [])[0])

    def test_rollback_rejects_additional_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            marker = root / 'dev/operations/tracing-agent/payment.enabled'
            marker.parent.mkdir(parents=True)
            marker.touch()
            self.assertFalse(dict(state.selection_checks('app', root))['payment Agent selection removed'])

    def test_rollback_checks_additional_deployments_without_marker(self):
        resources = {name: deployment(name) for name in ('gateway', 'order', 'chatbot')}
        resources['payment'] = deployment('payment', applied=True)
        read = lambda namespace, kind, name: resources.get(name, {}) if kind == 'deployment' else {}
        result = dict(state.app_checks(read, False, state.SERVICES))
        self.assertFalse(result['payment Agent wiring'])
        self.assertTrue(result['chatbot Agent wiring'])
        self.assertNotIn('search rollout', result)

    def test_kubernetes_read_failure_reports_only_requested_resource(self):
        error = subprocess.CalledProcessError(1, ['kubectl'], stderr='fixture private stderr')
        with mock.patch.object(state.subprocess, 'check_output', side_effect=error):
            with self.assertRaises(RuntimeError) as failure:
                state.get('monitoring', 'deployment', 'tempo')
        self.assertEqual(str(failure.exception), 'Kubernetes read failed for monitoring/deployment/tempo')
