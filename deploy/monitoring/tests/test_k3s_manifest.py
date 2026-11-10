"""deploy/k3s/k3s.py: compose 서비스 설정을 k3s 오브젝트로 바꾸는 규칙. 서버 · 클러스터 없이 돈다"""
import base64
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('k3s', Path(__file__).resolve().parents[2] / 'k3s' / 'k3s.py')
k3s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(k3s)


class K3sManifestTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.project = Path(tmp.name)
        (self.project / 'jwt').mkdir()
        (self.project / 'jwt/private.pem').write_text('PRIVATE')
        (self.project / 'jwt/public.pem').write_text('PUBLIC')

    def service(self, **extra):
        data = {
            'image': 'ghcr.io/x/member-service:abc',
            'mem_limit': str(512 * 1024 * 1024),
            'environment': {'SPRING_PROFILES_ACTIVE': 'dev', 'DB_PASSWORD': 'secret', 'MAIL_PASSWORD': None},
            'volumes': [
                {'type': 'bind', 'source': str(self.project / 'jwt'), 'target': '/app/jwt', 'read_only': True},
                {'type': 'bind', 'source': str(self.project / 'logs'), 'target': '/app/logs'},
            ],
        }
        data.update(extra)
        return data

    def items(self, name='member', service=None):
        result = k3s.manifest('dev', name, 30204, service or self.service(), self.project)
        return {item['kind'] + '/' + item['metadata']['name']: item for item in result['items']}

    def test_compose_settings_become_deployment_and_node_port(self):
        items = self.items()
        container = items['Deployment/member']['spec']['template']['spec']['containers'][0]
        self.assertEqual(container['image'], 'ghcr.io/x/member-service:abc')
        self.assertEqual(container['resources']['limits'], {'memory': '512Mi'})
        self.assertEqual(container['resources']['requests'], {'cpu': '50m', 'memory': '512Mi'})
        self.assertNotIn('replicas', items['Deployment/member']['spec'])
        self.assertEqual(items['Service/member']['spec']['ports'][0]['nodePort'], 30204)
        self.assertEqual(items['Deployment/member']['metadata']['namespace'], 'kidly-dev')
        self.assertNotIn('HorizontalPodAutoscaler/member', items)

    def test_service_without_node_port_is_cluster_internal(self):
        result = k3s.manifest('dev', 'member', None, self.service(), self.project)
        service = [item for item in result['items'] if item['kind'] == 'Service'][0]['spec']
        self.assertEqual(service['type'], 'ClusterIP')
        self.assertNotIn('nodePort', service['ports'][0])

    def test_environment_goes_to_secret_without_unset_values(self):
        variables = self.items()['Secret/member-env']['stringData']
        self.assertEqual(variables, {'SPRING_PROFILES_ACTIVE': 'dev', 'DB_PASSWORD': 'secret'})

    def test_jwt_folder_is_mounted_from_secret_and_logs_from_empty_dir(self):
        items = self.items()
        data = items['Secret/member-jwt']['data']
        self.assertEqual(base64.b64decode(data['private.pem']), b'PRIVATE')
        pod = items['Deployment/member']['spec']['template']['spec']
        mounts = {mount['mountPath']: mount for mount in pod['containers'][0]['volumeMounts']}
        self.assertEqual(mounts['/app/jwt']['name'], 'jwt')
        self.assertIn({'name': 'logs', 'emptyDir': {'sizeLimit': '256Mi'}}, pod['volumes'])

    def test_jwt_secret_is_readable_by_app_group(self):
        items = self.items()
        spec = items['Deployment/member']['spec']['template']['spec']
        self.assertEqual(spec['securityContext']['fsGroup'], 1001)
        jwt = next(v for v in spec['volumes'] if v['name'] == 'jwt')
        self.assertEqual(jwt['secret']['defaultMode'], 0o440)

    def test_service_without_keys_has_no_jwt_secret(self):
        items = self.items('payment', self.service(volumes=[]))
        self.assertNotIn('Secret/payment-jwt', items)

    def test_other_host_mounts_are_rejected(self):
        service = self.service(volumes=[{'type': 'bind', 'source': '/etc', 'target': '/host-etc'}])
        with self.assertRaisesRegex(ValueError, '/host-etc'):
            self.items(service=service)

    def test_changed_environment_restarts_pods(self):
        before = self.items()['Deployment/member']['spec']['template']['metadata']['annotations']
        changed = self.service(environment={'SPRING_PROFILES_ACTIVE': 'dev', 'DB_PASSWORD': 'rotated'})
        after = self.items(service=changed)['Deployment/member']['spec']['template']['metadata']['annotations']
        self.assertNotEqual(before, after)
        self.assertNotIn('rotated', json.dumps(after))

    def test_recommend_autoscales_on_cpu(self):
        items = self.items('recommend', self.service(volumes=[], mem_limit=str(448 * 1024 * 1024)))
        hpa = items['HorizontalPodAutoscaler/recommend']['spec']
        self.assertEqual((hpa['minReplicas'], hpa['maxReplicas']), (1, 3))
        container = items['Deployment/recommend']['spec']['template']['spec']['containers'][0]
        self.assertEqual(container['resources']['requests']['cpu'], '200m')

    def test_rolling_update_keeps_old_pod_until_new_is_ready(self):
        strategy = self.items()['Deployment/member']['spec']['strategy']
        self.assertEqual(strategy['rollingUpdate'], {'maxSurge': 1, 'maxUnavailable': 0})

    def test_services_with_unlocked_schedulers_never_run_twice(self):
        for name in ('product', 'notification'):
            strategy = self.items(name, self.service(volumes=[]))['Deployment/' + name]['spec']['strategy']
            self.assertEqual(strategy, {'type': 'Recreate'})

    def test_deploy_script_asks_which_services_stop_first(self):
        for name, code in (('product', 0), ('member', 1)):
            with self.assertRaises(SystemExit) as stop:
                k3s.main(['k3s.py', 'single-instance', 'dev', name])
            self.assertEqual(stop.exception.code, code)

    def test_pull_secret_needs_ghcr_login(self):
        config = self.project / 'docker'
        config.mkdir()
        (config / 'config.json').write_text(json.dumps({'auths': {}}))
        with self.assertRaisesRegex(ValueError, 'ghcr.io'):
            k3s.pull_secret('dev', config)
        (config / 'config.json').write_text(json.dumps({'auths': {'ghcr.io': {'auth': 'dTpw'}}}))
        payload = json.loads(base64.b64decode(k3s.pull_secret('dev', config)['data']['.dockerconfigjson']))
        self.assertEqual(payload, {'auths': {'ghcr.io': {'auth': 'dTpw'}}})


if __name__ == '__main__':
    unittest.main()
