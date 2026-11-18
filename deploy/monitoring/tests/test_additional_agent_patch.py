import importlib.util
from pathlib import Path
import unittest

script = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'agent-patch.py'
spec = importlib.util.spec_from_file_location('additional_agent_patch', script)
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


class AdditionalAgentPatchTest(unittest.TestCase):
    def test_selected_service_apply_and_rollback_preserve_sampling(self):
        for service in ('payment', 'chatbot'):
            with self.subTest(service=service):
                live = {
                    'kind': 'Deployment',
                    'metadata': {'name': service, 'namespace': 'kidly-dev', 'resourceVersion': '1'},
                    'spec': {'template': {
                        'metadata': {'labels': {'app': service}},
                        'spec': {'containers': [{
                            'name': service, 'envFrom': [{'secretRef': {'name': service + '-env'}}],
                        }]},
                    }},
                }
                delta = patcher.render_patch(live, '-Xmx384m', service=service)['spec']['template']
                container = delta['spec']['containers'][0]
                self.assertIn({'name': 'OTEL_TRACES_SAMPLER_ARG', 'value': '0.05'}, container['env'])
                with self.assertRaises(ValueError):
                    patcher.render_patch(live, '-Xmx384m', service='product')
                with self.assertRaises(ValueError):
                    patcher.render_patch(live, '-Xmx384m', service='chatbot' if service == 'payment' else 'payment')
                template = live['spec']['template']
                template['metadata']['labels'].update(delta['metadata']['labels'])
                template['spec']['containers'][0].update(container)
                template['spec']['volumes'] = delta['spec']['volumes']
                result = patcher.render_patch(live, '-Xmx384m', rollback=True, service=service)
                self.assertEqual(result['spec']['template']['metadata']['labels'], {'kidly/tracing': None})
