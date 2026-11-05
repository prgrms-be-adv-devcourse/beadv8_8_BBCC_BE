"""No live endpoints or real response bodies are used by these tests."""
import importlib.util
from pathlib import Path
import unittest
import urllib.error
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location('external_probe', Path(__file__).parents[1] / 'external_probe.py')
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class ExternalProbeTests(unittest.TestCase):
    def test_rejects_credentials_and_unencrypted_endpoint(self):
        for url in ('http://example.invalid/', 'https://user:password@example.invalid/'):
            with self.assertRaises(ValueError):
                probe.observe('test', url)

    def test_refuses_cross_origin_redirect(self):
        request = probe.urllib.request.Request('https://example.invalid/')
        with self.assertRaises(urllib.error.URLError):
            probe.SameOriginRedirect().redirect_request(request, None, 302, '', {}, 'https://other.invalid/')

    def test_health_payload_is_checked_but_not_returned(self):
        tls = MagicMock()
        tls.wrap_socket.return_value.__enter__.return_value.getpeercert.return_value = {
            'notAfter': 'Jan 01 00:00:00 2030 GMT'}
        response = MagicMock(status=200)
        response.read.return_value = b'{"status":"DOWN","private":"synthetic-secret"}'
        opener = MagicMock()
        opener.open.return_value.__enter__.return_value = response
        with patch.object(probe.ssl, 'create_default_context', return_value=tls), \
                patch.object(probe.socket, 'create_connection'), \
                patch.object(probe.urllib.request, 'build_opener', return_value=opener):
            result = probe.observe('test', 'https://example.invalid/', 'status', 'UP')
        self.assertFalse(result['ok'])
        self.assertNotIn('synthetic-secret', repr(result))

    def test_network_failure_is_a_failure_not_zero_latency_success(self):
        with patch.object(probe.socket, 'create_connection', side_effect=OSError('synthetic-secret')):
            result = probe.observe('test', 'https://example.invalid/')
        self.assertFalse(result['ok'])
        self.assertEqual(result['error'], 'connection_tls_or_health_error')
        self.assertNotIn('synthetic-secret', repr(result))


if __name__ == '__main__':
    unittest.main()
