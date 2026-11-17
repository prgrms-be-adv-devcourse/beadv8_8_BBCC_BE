import importlib.util
from pathlib import Path
import unittest

script = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'agent-patch.py'
spec = importlib.util.spec_from_file_location('agent_patch', script)
agent_patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(agent_patch)


def deployment(name='search'):
    return {
        'kind': 'Deployment',
        'metadata': {'name': name, 'namespace': 'kidly-dev', 'resourceVersion': '12'},
        'spec': {'template': {
            'metadata': {'labels': {'app': name}},
            'spec': {'containers': [{
                'name': name, 'envFrom': [{'secretRef': {'name': f'{name}-env'}}],
            }]},
        }},
    }


class SearchAgentPatchTest(unittest.TestCase):
    def test_search_adds_allowed_pod_label_and_five_percent_sampling(self):
        patch = agent_patch.render_patch(deployment(), '-Xmx384m', search=True)
        template = patch['spec']['template']
        self.assertEqual(template['metadata']['labels'], {'kidly/tracing': 'enabled'})
        self.assertIn({'name': 'OTEL_TRACES_SAMPLER_ARG', 'value': '0.05'},
                      template['spec']['containers'][0]['env'])

    def test_rollback_removes_search_pod_label(self):
        live = deployment()
        patch = agent_patch.render_patch(live, '-Xmx384m', search=True)['spec']['template']
        template = live['spec']['template']
        template['metadata']['labels'].update(patch['metadata']['labels'])
        template['spec']['containers'][0].update(patch['spec']['containers'][0])
        template['spec']['volumes'] = patch['spec']['volumes']
        rollback = agent_patch.render_patch(live, '-Xmx384m', rollback=True, search=True)
        self.assertEqual(rollback['spec']['template']['metadata']['labels'], {'kidly/tracing': None})

    def test_search_and_pilot_modes_reject_each_others_deployments(self):
        with self.assertRaises(ValueError):
            agent_patch.render_patch(deployment('gateway'), '-Xmx384m', search=True)
        with self.assertRaises(ValueError):
            agent_patch.render_patch(deployment(), '-Xmx384m')

    def test_apply_rejects_a_mismatched_or_existing_label(self):
        for labels in ({'app': 'order'}, {'app': 'search', 'kidly/tracing': 'enabled'}):
            with self.subTest(labels=labels):
                live = deployment()
                live['spec']['template']['metadata']['labels'] = labels
                with self.assertRaises(ValueError):
                    agent_patch.render_patch(live, '-Xmx384m', search=True)
