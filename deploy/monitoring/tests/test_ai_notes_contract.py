import importlib.util
import json
from dataclasses import asdict, replace
from pathlib import Path
import unittest
from unittest.mock import create_autospec

SPEC = importlib.util.spec_from_file_location('ai_contracts', Path(__file__).parents[1] / 'ai-notes/contracts.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


def context():
    return m.AnalysisContext(1, 'snapshot-1', 1000, 2000, ('dev',),
                             (m.Evidence('f1', 'memory_available_percent', 8, 'percent', 'global'),),
                             ('No per-container memory evidence',))


def report(provider='test-ai'):
    return m.AnalysisReport(1, 'snapshot-1', provider, 'test-model', 2010,
                            (m.Finding('Memory available 8%', 'Possible pressure, not confirmed',
                                       ('Check container memory',), 'Review deployments before changing capacity', ('f1',)),),
                            ('No per-container memory evidence',))


def ports():
    provider = create_autospec(m.AnalysisProvider, instance=True)
    provider.analyze.return_value = report()
    publisher = create_autospec(m.NotePublisher, instance=True)
    publisher.publish.return_value = 'note-1'
    return provider, publisher


class AnalysisContractTest(unittest.TestCase):
    def test_ai_adapters_use_same_contract_and_receive_deadline(self):
        for name in ('hosted-ai', 'local-model'):
            provider, publisher = ports()
            provider.analyze.return_value = report(name)
            result = m.analyze_and_publish(context(), provider, publisher, principal='worker-1', clock=lambda: 2020)
            self.assertEqual(result, 'note-1')
            provider.analyze.assert_called_once_with(context(), 2320)
            publication = publisher.publish.call_args.args[0]
            self.assertEqual(publication.report.provider, name)
            self.assertEqual(publication.source, 'server-worker')
            self.assertEqual(publication.published_at, 2020)

    def test_mcp_submission_uses_authoritative_stored_context(self):
        _, publisher = ports()
        store = create_autospec(m.ContextStore, instance=True)
        store.get.return_value = m.AnalysisTicket(context(), 2020)
        m.submit_analysis('snapshot-1', report(), store, publisher, principal='mcp-account', clock=lambda: 2025)
        store.get.assert_called_once_with('snapshot-1')
        publication = publisher.publish.call_args.args[0]
        self.assertEqual(publication.context.evidence[0].value, 8)
        self.assertEqual(publication.source, 'mcp-client')
        self.assertEqual(publication.principal, 'mcp-account')

    def test_unknown_stored_context_cannot_publish(self):
        _, publisher = ports()
        store = create_autospec(m.ContextStore, instance=True)
        store.get.side_effect = KeyError
        with self.assertRaises(KeyError):
            m.submit_analysis('fabricated', report(), store, publisher, principal='mcp-account', clock=lambda: 2020)
        publisher.publish.assert_not_called()

    def test_context_expiry_during_bounded_inference_is_visible_not_rejected(self):
        provider, publisher = ports()
        provider.analyze.return_value = replace(report(), generated_at=5605)
        times = iter((5590, 5620))
        m.analyze_and_publish(context(), provider, publisher, principal='worker', clock=lambda: next(times))
        publication = publisher.publish.call_args.args[0]
        self.assertTrue(publication.stale_at_publish)

    def test_exceeded_deadline_does_not_publish(self):
        provider, publisher = ports()
        times = iter((2020, 2321))
        with self.assertRaises(TimeoutError):
            m.analyze_and_publish(context(), provider, publisher, principal='worker', clock=lambda: next(times))
        publisher.publish.assert_not_called()

    def test_invalid_context_rejected_before_inference(self):
        for invalid in (replace(context(), version=True), replace(context(), environments='dev'),
                        replace(context(), limitations='abc'), replace(context(), evidence=context().evidence * 2),
                        replace(context(), evidence=(replace(context().evidence[0], value=float('nan')),)),
                        replace(context(), evidence=(replace(context().evidence[0], environment='prod'),))):
            provider, publisher = ports()
            with self.assertRaises(ValueError):
                m.analyze_and_publish(invalid, provider, publisher, principal='worker', clock=lambda: 2020)
            provider.analyze.assert_not_called()
            publisher.publish.assert_not_called()

    def test_stale_at_start_rejected_before_inference(self):
        provider, publisher = ports()
        with self.assertRaises(ValueError):
            m.analyze_and_publish(context(), provider, publisher, principal='worker', clock=lambda: 6001)
        provider.analyze.assert_not_called()

    def test_string_collections_duplicate_unknown_evidence_and_bad_times_rejected(self):
        finding = report().findings[0]
        invalids = (replace(report(), context_id='another'), replace(report(), limitations='abc'),
                    replace(report(), generated_at=1999), replace(report(), generated_at=3000),
                    replace(report(), findings=(replace(finding, checks='abc'),)),
                    replace(report(), findings=(replace(finding, evidence_ids=('f1', 'f1')),)),
                    replace(report(), findings=(replace(finding, evidence_ids=('fabricated',)),)))
        for invalid in invalids:
            with self.assertRaises(ValueError):
                m.validate_report(context(), invalid, 2020)

    def test_wire_parser_accepts_json_lists_but_rejects_unknown_fields_and_strings(self):
        data = json.loads(json.dumps(asdict(report())))
        self.assertEqual(m.report_from_dict(data), report())
        bad = dict(data, context={'fake': 'metric'})
        with self.assertRaises(ValueError):
            m.report_from_dict(bad)
        data['findings'][0]['checks'] = 'abc'
        with self.assertRaises(ValueError):
            m.report_from_dict(data)

    def test_provider_failure_does_not_publish_or_fallback(self):
        provider, publisher = ports()
        provider.analyze.side_effect = TimeoutError
        with self.assertRaises(TimeoutError):
            m.analyze_and_publish(context(), provider, publisher, principal='worker', clock=lambda: 2020)
        self.assertEqual(provider.analyze.call_count, 1)
        publisher.publish.assert_not_called()

    def test_publisher_failure_propagates_without_paid_reinference(self):
        provider, publisher = ports()
        publisher.publish.side_effect = OSError
        with self.assertRaises(OSError):
            m.analyze_and_publish(context(), provider, publisher, principal='worker', clock=lambda: 2020)
        provider.analyze.assert_called_once()
        publisher.publish.assert_called_once()

    def test_digest_is_stable_across_retries_and_excludes_receipt_time(self):
        _, publisher = ports()
        store = create_autospec(m.ContextStore, instance=True)
        store.get.return_value = m.AnalysisTicket(context(), 2020)
        for now in (2025, 2030):
            m.submit_analysis('snapshot-1', report(), store, publisher, principal='account', clock=lambda: now)
        a, b = [call.args[0] for call in publisher.publish.call_args_list]
        self.assertEqual(a.content_digest, b.content_digest)
        self.assertNotEqual(a.published_at, b.published_at)

    def test_empty_output_requires_explanation_and_control_characters_rejected(self):
        with self.assertRaises(ValueError):
            m.validate_report(context(), replace(report(), findings=(), limitations=()), 2020)
        m.validate_report(context(), replace(report(), findings=(), limitations=('Insufficient evidence',)), 2020)
        with self.assertRaises(ValueError):
            m.text('unsafe\x00')


if __name__ == '__main__':
    unittest.main()
