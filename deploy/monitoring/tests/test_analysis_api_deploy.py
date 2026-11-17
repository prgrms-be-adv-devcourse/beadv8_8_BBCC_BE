"""Deployment boundary for the Grafana AI analysis button (ai-notes/analysis-api.sh)."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT.parents[1] / '.github/workflows/monitoring-deploy.yml'


class AnalysisApiDeployTest(unittest.TestCase):
    def test_ai_keys_never_become_kubernetes_secrets(self):
        kustomization = (ROOT / 'kustomization.yaml').read_text()
        self.assertNotIn('GROQ_API_KEY', kustomization)
        self.assertNotIn('OPENAI_API_KEY', kustomization)
        self.assertIn('  - k8s/ai-analysis.yaml\n', kustomization)

    def test_route_exposes_only_run_post_and_status_get(self):
        manifest = (ROOT / 'k8s/ai-analysis.yaml').read_text()
        match = ' '.join(manifest.split('match: >-', 1)[1].split('services:', 1)[0].split())
        self.assertEqual(match, 'Host(`grafana.kidly.kro.kr`) && ((Method(`POST`) && Path(`/kidly-ai-analysis/run`)) '
                                '|| (Method(`GET`) && Path(`/kidly-ai-analysis/status`)))')
        self.assertEqual(manifest.count('match:'), 1)
        # The endpoint is the node process set by analysis-api.sh, not a Pod selector.
        self.assertNotIn('selector:', manifest)

    def test_launcher_passes_key_paths_only(self):
        script = (ROOT / 'ai-notes/analysis-api.sh').read_text()
        # Key files are read by the handler on each run, never expanded into argv, env or output.
        self.assertNotRegex(script, r'\$\(\s*(<|cat)\s*secrets/')
        self.assertIn('--key-file secrets/GROQ_API_KEY', script)
        self.assertIn('--allow-paid-openai --openai-key-file secrets/OPENAI_API_KEY', script)
        self.assertRegex(script, r'if \[\[ -s secrets/OPENAI_API_KEY \]\]')
        self.assertIn('> /dev/null 2>&1 < /dev/null &', script)

    def test_deploy_restarts_handler_and_registers_reboot(self):
        deploy = (ROOT / 'deploy.sh').read_text()
        self.assertIn('if ! bash ai-notes/analysis-api.sh; then', deploy)
        self.assertIn("@reboot bash /opt/app/monitoring/ai-notes/analysis-api.sh", deploy)

    def test_workflow_writes_optional_keys_without_printing(self):
        workflow = WORKFLOW.read_text()
        self.assertIn('GROQ_API_KEY: ${{ secrets.GROQ_API_KEY }}', workflow)
        self.assertIn('OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}', workflow)
        self.assertIsNone(re.search(r'echo[^\n]*\$\{!key\}|echo[^\n]*\$value', workflow))


if __name__ == '__main__':
    unittest.main()
