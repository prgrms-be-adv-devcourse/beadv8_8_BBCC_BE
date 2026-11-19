#!/usr/bin/env python3
"""Bounded external observation. Never print response bodies or accept credentials."""
import argparse
import json
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request


class SameOriginRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        old = urllib.parse.urlsplit(request.full_url)
        new = urllib.parse.urlsplit(new_url)
        if new.scheme != 'https' or (new.hostname, new.port) != (old.hostname, old.port):
            raise urllib.error.URLError('cross-origin redirect refused')
        return super().redirect_request(request, response, code, message, headers, new_url)


def observe(name, url, health_key=None, health_value=None, expected_status=200):
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != 'https' or parts.username or parts.password:
        raise ValueError('Only credential-free HTTPS is allowed')
    started = time.monotonic()
    result = {'target': name, 'ok': False, 'status': None, 'tls_days': None}
    try:
        context = ssl.create_default_context()
        with socket.create_connection((parts.hostname, parts.port or 443), timeout=5) as connection:
            with context.wrap_socket(connection, server_hostname=parts.hostname) as tls:
                expires = ssl.cert_time_to_seconds(tls.getpeercert()['notAfter'])
                result['tls_days'] = int((expires - time.time()) // 86400)
        opener = urllib.request.build_opener(SameOriginRedirect(), urllib.request.HTTPSHandler(context=context))
        try:
            response = opener.open(url, timeout=5)
        except urllib.error.HTTPError as error:
            if error.code != expected_status:
                raise
            response = error
        with response as response:
            result['status'] = response.status
            # Health endpoints need their actual health field, not just an HTTP 200 page.
            if health_key:
                body = response.read(16_385)
                if len(body) > 16_384:
                    raise ValueError('health response too large')
                payload = json.loads(body)
                healthy = isinstance(payload, dict) and payload.get(health_key) == health_value
            else:
                healthy = True  # Representative GET checks reachability only, not business correctness.
            result['ok'] = response.status == expected_status and healthy
    except urllib.error.HTTPError as error:
        result['status'] = error.code
        result['error'] = 'http_error'
    except (OSError, ValueError, KeyError):
        result['error'] = 'connection_tls_or_health_error'
    result['duration_ms'] = round((time.monotonic() - started) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment', choices=('dev', 'prod'), default='dev')
    args = parser.parse_args()
    host = 'api.kidly.kro.kr'
    rows = [observe(args.environment + '-gateway-product-read', 'https://' + host + '/api/v1/products?page=0&size=1'),
            observe('grafana-health', 'https://grafana.kidly.kro.kr/api/health', 'database', 'ok')]
    print(json.dumps({'environment': args.environment, 'observations': rows}, ensure_ascii=False))
    return 0 if all(row['ok'] for row in rows) else 1


if __name__ == '__main__':
    raise SystemExit(main())
