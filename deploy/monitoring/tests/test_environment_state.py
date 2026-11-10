"""Lifecycle failure/intent tests without connecting to any server or starting containers."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('state', Path(__file__).resolve().parents[2] / 'environment-state.py')
state = importlib.util.module_from_spec(spec)
spec.loader.exec_module(state)


class EnvironmentStateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.root_patch = patch.object(state, 'ROOT', self.root)
        self.state_patch = patch.object(state, 'STATE', self.root / 'monitoring/operations')
        self.root_patch.start()
        self.state_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.addCleanup(self.state_patch.stop)
        env = self.root / 'dev'
        env.mkdir()
        (env / '.env').write_text('COMPOSE_PROFILES=search\nSEARCH_PORT=8082\nDB_PASSWORD=do-not-print\n')
        self.data = {'env': 'dev', 'enabled': True, 'phase': 'running', 'changed_at': 100,
                     'jobs': ['kidly-search']}

    def test_legacy_inventory_is_replaced_by_current_targets(self):
        state.write(dict(self.data, jobs=['kidly', 'kidly-search']))
        legacy = self.root / 'monitoring/targets/dev.yml'
        legacy.parent.mkdir(parents=True, exist_ok=True)
        legacy.write_text('old target')
        self.assertEqual(state.snapshot('dev')['jobs'], ['kidly-search'])
        state.register_targets('dev')
        self.assertFalse(legacy.exists())
        self.assertIn('127.0.0.1:8082', (legacy.parent / 'search/dev.yml').read_text())

    def test_missing_targets_does_not_unregister_inventory(self):
        state.write(self.data)
        self.assertEqual(state.snapshot('dev')['jobs'], self.data['jobs'])
        state.write(self.data, monitoring=True)
        (state.STATE / 'dev.prom').unlink()
        self.assertIn('kidly_environment_registered{env="dev"} 1', (state.STATE / 'inventory.prom').read_text())

    def test_stop_removes_only_discovery_and_preserves_expected_jobs(self):
        state.write(self.data)
        state.register_targets('dev')
        with patch.object(state, 'run') as run:
            result = state.control('dev', 'stop')
        self.assertFalse(result['enabled'])
        self.assertEqual(result['phase'], 'stopped')
        self.assertFalse((self.root / 'monitoring/targets/dev.yml').exists())
        self.assertEqual(state.load('dev')['jobs'], self.data['jobs'])
        self.assertNotIn('--volumes', repr(run.call_args_list))
        self.assertNotIn('down', repr(run.call_args_list))

    def test_failed_stop_restores_local_monitoring_intent(self):
        state.write(self.data)
        with patch.object(state, 'run', side_effect=RuntimeError('stop failed')):
            with self.assertRaises(RuntimeError):
                state.control('dev', 'stop')
        self.assertTrue(state.load('dev')['enabled'])

    def test_resume_allows_other_environment_and_preserves_its_intent(self):
        stopped = dict(self.data, enabled=False, phase='stopped')
        state.write(stopped)
        prod = dict(self.data, env='prod')
        state.write(prod)
        with patch.object(state, 'run', return_value='') as run:
            result = state.control('dev', 'resume')
        self.assertEqual(result['phase'], 'running')
        self.assertEqual(state.load('prod'), prod)
        self.assertEqual(run.call_count, 1)
        self.assertEqual(run.call_args.args[0][run.call_args.args[0].index('-p') + 1], 'dev')

    def test_stop_dev_preserves_prod_targets_and_intent(self):
        prod = dict(self.data, env='prod')
        state.write(prod)
        directory = self.root / 'prod'
        directory.mkdir()
        (directory / '.env').write_text('COMPOSE_PROFILES=search\nSEARCH_PORT=8094\n')
        state.register_targets('dev')
        state.register_targets('prod')
        with patch.object(state, 'run'):
            state.control('dev', 'stop')
        self.assertTrue((self.root / 'monitoring/targets/search/prod.yml').exists())
        self.assertEqual(state.load('prod'), prod)

    def test_failed_resume_remains_expected_and_transitioning(self):
        state.write(dict(self.data, enabled=False, phase='stopped'))
        with patch.object(state, 'run', side_effect=RuntimeError('startup failed')):
            with self.assertRaises(RuntimeError):
                state.control('dev', 'resume')
        self.assertTrue(state.load('dev')['enabled'])
        self.assertEqual(state.load('dev')['phase'], 'starting')

    def test_resume_registers_actual_profiles_without_pull_or_data_deletion(self):
        state.write(dict(self.data, enabled=False, phase='stopped'))
        with patch.object(state, 'run', return_value='') as run:
            result = state.control('dev', 'resume')
        self.assertEqual(result['phase'], 'running')
        self.assertTrue((self.root / 'monitoring/targets/search/dev.yml').exists())
        self.assertIn("'never'", repr(run.call_args_list))

    def test_product_profile_survives_resume_and_stop(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=search,product\nSEARCH_PORT=8082\nPRODUCT_PORT=8099\n')
        with patch.object(state, 'run', return_value=''):
            running = state.control('dev', 'resume')
            target = self.root / 'monitoring/targets/product/dev.yml'
            self.assertIn('kidly-product', running['jobs'])
            self.assertIn('127.0.0.1:8099', target.read_text())
            stopped = state.control('dev', 'stop')
        self.assertFalse(target.exists())
        self.assertIn('kidly-product', stopped['jobs'])

    def test_order_profile_survives_resume_and_stop(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=search,order\nSEARCH_PORT=8082\nORDER_PORT=8100\n')
        with patch.object(state, 'run', return_value=''):
            running = state.control('dev', 'resume')
            target = self.root / 'monitoring/targets/order/dev.yml'
            self.assertIn('kidly-order', running['jobs'])
            self.assertIn('127.0.0.1:8100', target.read_text())
            stopped = state.control('dev', 'stop')
        self.assertFalse(target.exists())
        self.assertIn('kidly-order', stopped['jobs'])

    def test_community_profile_survives_resume_and_stop(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=search,community\nSEARCH_PORT=8082\nCOMMUNITY_PORT=8102\n')
        with patch.object(state, 'run', return_value=''):
            running = state.control('dev', 'resume')
            target = self.root / 'monitoring/targets/community/dev.yml'
            self.assertIn('kidly-community', running['jobs'])
            self.assertIn('127.0.0.1:8102', target.read_text())
            stopped = state.control('dev', 'stop')
        self.assertFalse(target.exists())
        self.assertIn('kidly-community', stopped['jobs'])

    def test_k3s_services_are_expected_without_local_target(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=gateway\nGATEWAY_PORT=8083\nKAFKA_NODE_PORT=30092\n'
            'RECOMMEND_NODE_PORT=30202\nMEMBER_NODE_PORT=30204\n')
        self.assertEqual(state.k3s_services('dev'), [('recommend-service', 'kidly-recommend', 'recommend', 30202),
                                                     ('member-service', 'kidly-member', 'member', 30204)])
        with patch.object(state, 'run', return_value=''):
            running = state.control('dev', 'resume')
            self.assertEqual(running['jobs'], ['kidly-gateway', 'kidly-recommend', 'kidly-member'])
            self.assertIn('kidly_app_expected{env="dev",app_job="kidly-member"} 1', state.metrics(running))
            self.assertFalse((self.root / 'monitoring/targets/recommend/dev.yml').exists())
            self.assertFalse((self.root / 'monitoring/targets/member/dev.yml').exists())
            stopped = state.control('dev', 'stop')
        self.assertIn('kidly-member', stopped['jobs'])

    def test_k3s_pods_start_before_gateway_and_stop_after_compose(self):
        (self.root / 'dev/.env').write_text('COMPOSE_PROFILES=gateway\nGATEWAY_PORT=8083\nMEMBER_NODE_PORT=30204\n')
        with patch.object(state, 'run', return_value='') as run:
            state.control('dev', 'resume')
            started = [call.args[0] for call in run.call_args_list]
            run.reset_mock()
            state.control('dev', 'stop')
            stopped = [call.args[0] for call in run.call_args_list]
        self.assertEqual(started[0][-3:], ['--all', '--replicas', '1'])
        self.assertIn('--for=condition=Available', started[1])
        self.assertEqual(started[2][:2], ['docker', 'compose'])
        self.assertEqual(stopped[0][:2], ['docker', 'compose'])
        self.assertEqual(stopped[1][-3:], ['--all', '--replicas', '0'])
        self.assertIn('kidly-dev', stopped[1])

    def test_compose_only_environment_does_not_touch_k3s(self):
        with patch.object(state, 'run', return_value='') as run:
            state.control('dev', 'resume')
            state.control('dev', 'stop')
        self.assertFalse(any(call.args[0][0] == 'kubectl' for call in run.call_args_list))

    def test_service_in_both_compose_and_k3s_is_rejected(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=recommend\nRECOMMEND_PORT=8087\nRECOMMEND_NODE_PORT=30202\n')
        with patch.object(state, 'run', return_value=''):
            with self.assertRaisesRegex(ValueError, 'expected services'):
                state.control('dev', 'resume')

    def test_invalid_node_port_is_rejected(self):
        (self.root / 'dev/.env').write_text('COMPOSE_PROFILES=gateway\nGATEWAY_PORT=8083\nMEMBER_NODE_PORT=8090\n')
        with self.assertRaisesRegex(ValueError, 'MEMBER_NODE_PORT'):
            state.k3s_services('dev')

    def test_kafka_exporter_has_separate_intent_and_discovery(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES=search,kafka-monitoring\nSEARCH_PORT=8082\n'
            'KAFKA_EXPORTER_PORT=9308\n')
        with patch.object(state, 'run', return_value=''):
            running = state.control('dev', 'resume')
            self.assertTrue(running['kafka_exporter_expected'])
            self.assertNotIn('kidly-kafka', running['jobs'])
            target = self.root / 'monitoring/targets/kafka/dev.yml'
            self.assertIn('127.0.0.1:9308', target.read_text())
            self.assertIn('kidly_kafka_exporter_expected{env="dev"} 1', state.metrics(running))
            stopped = state.control('dev', 'stop')
        self.assertFalse(target.exists())
        self.assertIn('kidly_kafka_exporter_expected{env="dev"} 0', state.metrics(stopped))

    def test_kafka_profile_whitespace_and_comments(self):
        (self.root / 'dev/.env').write_text(
            'COMPOSE_PROFILES="search, kafka-monitoring" # enabled\n'
            'KAFKA_EXPORTER_PORT=9308 # loopback port\nSEARCH_PORT=8082\n')
        self.assertEqual(9308, state.kafka_target('dev'))
        self.assertEqual({'kidly-search'}, {target[1] for target in state.configuration('dev')})

    def test_invalid_kafka_port_is_rejected_before_inventory_mutation(self):
        state.write(self.data, monitoring=True)
        original = (state.STATE / 'dev.json').read_text()
        for port in ('', '0', '65536', 'invalid'):
            with self.subTest(port=port):
                (self.root / 'dev/.env').write_text(
                    'COMPOSE_PROFILES=kafka-monitoring\n'
                    'KAFKA_EXPORTER_PORT=' + port + '\n')
                with self.assertRaisesRegex(ValueError, 'KAFKA_EXPORTER_PORT'):
                    state.snapshot('dev')
                self.assertEqual(original, (state.STATE / 'dev.json').read_text())

    def test_disabled_kafka_profile_needs_no_port(self):
        (self.root / 'dev/.env').write_text('COMPOSE_PROFILES=search\n')
        self.assertIsNone(state.kafka_target('dev'))

    def test_old_inventory_does_not_expect_uninstalled_exporter(self):
        self.assertIn('kidly_kafka_exporter_expected{env="dev"} 0', state.metrics(self.data))
        with self.assertRaises(ValueError):
            state.validate(dict(self.data, kafka_exporter_expected='true'))

    def test_invalid_state_does_not_replace_good_inventory(self):
        state.write(self.data, monitoring=True)
        for invalid in [dict(self.data, env='other'), dict(self.data, enabled='false'),
                        dict(self.data, jobs=['untrusted']), dict(self.data, phase='stopped')]:
            with self.assertRaises(ValueError):
                state.write(invalid, monitoring=True)
        self.assertEqual(state.load('dev'), self.data)
        self.assertNotIn('do-not-print', state.metrics(self.data))


if __name__ == '__main__':
    unittest.main()
