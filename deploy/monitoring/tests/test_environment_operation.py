import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[3] / '.github/scripts/environment-operation.py'
spec = importlib.util.spec_from_file_location('operation', path)
operation = importlib.util.module_from_spec(spec)
with patch.dict(os.environ, {'APP_SSH_HOST': 'app.test', 'MONITORING_SSH_HOST': 'monitoring.test'}):
    spec.loader.exec_module(operation)


class OperationTests(unittest.TestCase):
    def setUp(self):
        self.running = {'env': 'dev', 'enabled': True, 'phase': 'running', 'changed_at': 1, 'jobs': ['kidly']}

    def test_stop_timeout_never_stops_containers_and_restores_intent(self):
        writes = []
        actions = []
        def remote(host, action, env, data=None):
            actions.append(action)
            if action in ('snapshot', 'plan'):
                return self.running
            if action == 'inventory':
                writes.append(data)
        with patch.object(operation, 'install'), patch.object(operation, 'remote', side_effect=remote), \
                patch.object(operation, 'wait_disabled', side_effect=RuntimeError('not acknowledged')):
            with self.assertRaises(RuntimeError):
                operation.operate('stop', 'dev')
        self.assertNotIn('stop', actions)
        self.assertFalse(writes[0]['enabled'])
        self.assertTrue(writes[-1]['enabled'])

    def test_lost_app_connection_does_not_block_stop_monitoring_restore(self):
        writes = []
        snapshots = []
        def remote(host, action, env, data=None):
            if action == 'snapshot':
                snapshots.append(action)
                if len(snapshots) > 1:
                    raise RuntimeError('app connection lost')
                return self.running
            if action == 'plan':
                return self.running
            if action == 'inventory':
                writes.append(data)
            if action == 'stop':
                raise RuntimeError('app connection lost')
        with patch.object(operation, 'install'), patch.object(operation, 'remote', side_effect=remote), \
                patch.object(operation, 'wait_disabled'):
            with self.assertRaises(RuntimeError):
                operation.operate('stop', 'dev')
        self.assertEqual(len(snapshots), 1)
        self.assertTrue(writes[-1]['enabled'])

    def test_sync_without_deployment_does_not_clear_inventory(self):
        with patch.object(operation, 'install'), patch.object(operation, 'remote', return_value=None) as remote:
            operation.operate('sync', 'prod')
        self.assertEqual([call.args[1] for call in remote.call_args_list], ['snapshot'])

    def test_resume_capacity_refusal_does_not_change_monitoring(self):
        with patch.object(operation, 'install'), \
                patch.object(operation, 'remote', side_effect=RuntimeError('other environment running')) as remote:
            with self.assertRaises(RuntimeError):
                operation.operate('resume', 'dev')
        self.assertEqual(remote.call_args.args[1], 'check-capacity')

    def test_stop_acknowledgement_precedes_container_stop(self):
        order = []
        def remote(host, action, env, data=None):
            order.append(action)
            if action in ('snapshot', 'plan'):
                return self.running
            if action == 'stop':
                return dict(self.running, enabled=False, phase='stopped')
        with patch.object(operation, 'install'), patch.object(operation, 'remote', side_effect=remote), \
                patch.object(operation, 'wait_disabled', side_effect=lambda *args: order.append('ack')):
            operation.operate('stop', 'dev')
        self.assertLess(order.index('inventory'), order.index('ack'))
        self.assertLess(order.index('ack'), order.index('stop'))


if __name__ == '__main__':
    unittest.main()
