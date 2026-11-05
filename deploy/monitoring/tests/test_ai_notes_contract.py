import importlib.util
from dataclasses import replace
from pathlib import Path
import unittest
from unittest.mock import Mock

SPEC = importlib.util.spec_from_file_location('ai_contracts', Path(__file__).parents[1] / 'ai-notes/contracts.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


def context():
    return m.AnalysisContext(1, 'snapshot-1', 1000, 2000, ('dev',),
                             (m.Evidence('f1', 'memory_available_percent', 8, 'percent'),),
                             ('No per-container memory evidence',))


def report(provider='test-ai'):
    return m.AnalysisReport(1, 'snapshot-1', provider, 'test-model', 2010,
                            (m.Finding('Memory available 8%', 'Possible pressure, not confirmed',
                                       ('Check container memory',), 'Review recent deployments before changing capacity', ('f1',)),),
                            ('No per-container memory evidence',))


class AnalysisContractTest(unittest.TestCase):
    def test_two_ai_adapters_use_same_context_and_publisher(self):
        # Test doubles exercise substitution; they are not production inference implementations.
        for name in ('hosted-ai', 'local-model'):
            with self.subTest(provider=name):
                provider = Mock()
                provider.analyze.return_value = report(name)
                publisher = Mock()
                publisher.publish.return_value = 'note-1'
                self.assertEqual(m.analyze_and_publish(context(), provider, publisher, lambda: 2020), 'note-1')
                provider.analyze.assert_called_once_with(context())
                publisher.publish.assert_called_once_with(context(), report(name))

    def test_external_mcp_client_can_submit_same_report_without_server_ai(self):
        publisher = Mock()
        m.publish_analysis(context(), report(), publisher, 2020)
        publisher.publish.assert_called_once_with(context(), report())

    def test_slow_ai_uses_clock_after_inference(self):
        provider, publisher = Mock(), Mock()
        provider.analyze.return_value = replace(report(), generated_at=2200)
        times = iter((2000, 2201))
        m.analyze_and_publish(context(), provider, publisher, lambda: next(times))
        publisher.publish.assert_called_once()

    def test_unknown_evidence_or_context_cannot_publish(self):
        for invalid in (replace(report(), context_id='another'),
                        replace(report(), findings=(replace(report().findings[0], evidence_ids=('fabricated',)),))):
            publisher = Mock()
            with self.assertRaises(ValueError):
                m.publish_analysis(context(), invalid, publisher, 2020)
            publisher.publish.assert_not_called()

    def test_stale_context_and_unsupported_version_fail_before_model_call(self):
        for invalid in (replace(context(), window_end=1100), replace(context(), version=2)):
            provider, publisher = Mock(), Mock()
            with self.assertRaises(ValueError):
                m.analyze_and_publish(invalid, provider, publisher, lambda: 5000)
            provider.analyze.assert_not_called()
            publisher.publish.assert_not_called()

    def test_provider_failure_does_not_publish_or_fallback(self):
        provider, publisher = Mock(), Mock()
        provider.analyze.side_effect = TimeoutError
        with self.assertRaises(TimeoutError):
            m.analyze_and_publish(context(), provider, publisher, lambda: 2020)
        self.assertEqual(provider.analyze.call_count, 1)
        publisher.publish.assert_not_called()

    def test_nonfinite_and_duplicate_evidence_rejected(self):
        for invalid in (replace(context(), evidence=(replace(context().evidence[0], value=float('nan')),)),
                        replace(context(), evidence=context().evidence * 2)):
            with self.assertRaises(ValueError):
                m.validate_context(invalid, 2020)

    def test_empty_output_requires_explanation_not_silent_healthy(self):
        with self.assertRaises(ValueError):
            m.validate_report(context(), replace(report(), findings=(), limitations=()), 2020)
        m.validate_report(context(), replace(report(), findings=(), limitations=('Insufficient evidence',)), 2020)

    def test_report_size_and_future_timestamp_rejected(self):
        for invalid in (replace(report(), findings=report().findings * 6), replace(report(), generated_at=3000)):
            with self.assertRaises(ValueError):
                m.validate_report(context(), invalid, 2020)


if __name__ == '__main__':
    unittest.main()
