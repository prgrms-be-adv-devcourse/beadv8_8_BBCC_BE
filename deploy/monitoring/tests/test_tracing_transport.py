import argparse
import importlib.util
from pathlib import Path
import ssl
import unittest
from unittest.mock import Mock


path = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'verify-transport.py'
spec = importlib.util.spec_from_file_location('verify_transport', path)
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


class TracingTransportTests(unittest.TestCase):
    def test_private_address_is_required(self):
        for value in ('127.0.0.1', '8.8.8.8', 'example.com', '10.1.2.3:30438'):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                transport.private_address(value)
        self.assertEqual(transport.private_address('10.1.2.3'), '10.1.2.3')

    def test_acceptance_requires_tls_rejection_without_certificate(self):
        rejected = ssl.SSLError()
        rejected.reason = 'TLSV13_ALERT_CERTIFICATE_REQUIRED'
        send = Mock(side_effect=[200, rejected])
        self.assertTrue(transport.verify('10.1.2.3', object(), object(), send)[0])
        # HTTP errors do not prove TLS rejected an unauthenticated client.
        self.assertFalse(transport.verify('10.1.2.3', object(), object(),
                                          Mock(side_effect=[200, 403]))[0])
        failed_auth = Mock(return_value=503)
        self.assertFalse(transport.verify('10.1.2.3', object(), object(), failed_auth)[0])
        failed_auth.assert_called_once()
