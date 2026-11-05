"""Security regression checks. No credentials or real mutation requests."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location('mcp_' + name, ROOT / 'mcp' / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


boundary = module('update_boundary')
launcher = module('launch')
bootstrap = module('bootstrap')


class McpBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.node = {'metadata': {'name': 'infra'}, 'spec': {'podCIDR': '10.42.0.0/24'}}
        self.pod = {'metadata': {}, 'spec': {'nodeName': 'infra'}, 'status': {
            'phase': 'Running', 'podIP': '10.42.0.42', 'conditions': [{'type': 'Ready', 'status': 'True'}]}}

    def test_only_one_ready_pod_host_address_is_allowed(self):
        self.assertEqual('10.42.0.42/32', boundary.grafana_source({'items': [self.pod]}, self.node))
        for items in ([], [self.pod, self.pod]):
            with self.assertRaises(ValueError):
                boundary.grafana_source({'items': items}, self.node)
        for address in ('10.42.0.1', '10.42.0.0', '10.42.0.255', '192.0.2.42', '127.0.0.1'):
            pod = copy.deepcopy(self.pod)
            pod['status']['podIP'] = address
            with self.assertRaises(ValueError):
                boundary.grafana_source({'items': [pod]}, self.node)

    def test_not_ready_deleting_or_wrong_node_never_grants_access(self):
        for change in ('not-ready', 'deleting', 'other-node'):
            pod = copy.deepcopy(self.pod)
            if change == 'not-ready':
                pod['status']['conditions'][0]['status'] = 'False'
            elif change == 'deleting':
                pod['metadata']['deletionTimestamp'] = '2026-11-05T00:00:00Z'
            else:
                pod['spec']['nodeName'] = 'other'
            with self.assertRaises(ValueError):
                boundary.grafana_source({'items': [pod]}, self.node)

    def test_launcher_does_not_inherit_other_credentials_or_overrides(self):
        policy = {'grafana_url': 'https://grafana.kidly.kro.kr'}
        with patch.dict(os.environ, {'GRAFANA_PASSWORD': 'SYNTHETIC', 'GRAFANA_API_KEY': 'SYNTHETIC',
                                   'AWS_SECRET_ACCESS_KEY': 'SYNTHETIC', 'GRAFANA_EXTRA_HEADERS': 'SYNTHETIC'}):
            env = launcher.environment(policy, {'org_id': 2}, 'SYNTHETIC_TOKEN')
        self.assertFalse(any(key in env for key in ('GRAFANA_PASSWORD', 'GRAFANA_API_KEY',
                                                    'AWS_SECRET_ACCESS_KEY', 'GRAFANA_EXTRA_HEADERS')))
        self.assertEqual('false', env['GRAFANA_ALLOW_URL_OVERRIDE'])
        args = launcher.arguments(Path('/example/mcp-grafana'))
        self.assertIn('--disable-write', args)
        self.assertIn('--enabled-tools=datasource,prometheus', args)
        self.assertIn('--dynamic-multi-org=false', args)
        self.assertNotIn('SYNTHETIC_TOKEN', ' '.join(args))

    def test_never_expiring_or_overlong_tokens_are_refused_before_creation(self):
        api = Mock()
        for ttl in (0, -1, 604801):
            with patch.dict(os.environ, {'KIDLY_MCP_PRIVATE_CHANNEL': '1'}), patch('sys.stdout.isatty', return_value=False):
                with self.assertRaises(ValueError):
                    bootstrap.issue(api, {'token_ttl_seconds': ttl}, {'org_id': 2, 'account_id': 2})
        api.call.assert_not_called()

    def test_server_token_without_expiration_is_revoked(self):
        api = Mock()
        api.call.side_effect = [{'id': 7, 'key': 'SYNTHETIC'}, [{'id': 7, 'expiration': None}], {}]
        with patch.dict(os.environ, {'KIDLY_MCP_PRIVATE_CHANNEL': '1'}), patch('sys.stdout.isatty', return_value=False):
            with self.assertRaises(ValueError):
                bootstrap.issue(api, {'token_ttl_seconds': 604800}, {'org_id': 2, 'account_id': 2})
        api.call.assert_called_with('/api/serviceaccounts/2/tokens/7', 'DELETE', org=2)

    def test_routing_fallback_cannot_contact_prometheus(self):
        path = ROOT / 'k8s/mcp-prometheus.yaml'
        raw = subprocess.check_output(['ruby', '-rjson', '-ryaml', '-e',
                                       'puts JSON.generate(YAML.load_stream(File.read(ARGV[0])))', str(path)], text=True)
        resources = json.loads(raw)
        rules = next(r for r in resources if r['kind'] == 'IngressRoute')['spec']['routes']
        query, denied = rules
        self.assertGreater(query['priority'], denied['priority'])
        self.assertEqual([{'name': 'prometheus', 'port': 9090}], query['services'])
        self.assertNotIn('PathPrefix', query['match'])
        self.assertNotIn('Method(`DELETE`)', query['match'])
        self.assertNotIn('/api/v1/write', query['match'])
        self.assertNotIn('/api/v1/admin', query['match'])
        self.assertEqual([{'name': 'noop@internal', 'kind': 'TraefikService'}], denied['services'])
        source = next(r for r in resources if r['metadata']['name'] == 'mcp-prometheus-internal')
        self.assertEqual(['127.0.0.1/32'], source['spec']['ipAllowList']['sourceRange'])
        self.assertNotIn('ipStrategy', source['spec']['ipAllowList'])


if __name__ == '__main__':
    unittest.main()
