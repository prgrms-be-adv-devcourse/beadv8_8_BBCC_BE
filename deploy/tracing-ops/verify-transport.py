#!/usr/bin/env python3
"""Check the private Tempo OTLP receiver without generating a trace."""
import argparse
from http.client import HTTPSConnection
import ssl

from ipaddress import IPv4Address, IPv4Network


PRIVATE = tuple(map(IPv4Network, ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))
PORT = 30438
CERTIFICATE_REQUIRED = {
    'TLSV13_ALERT_CERTIFICATE_REQUIRED',
    'TLSV1_ALERT_CERTIFICATE_REQUIRED',
    'SSLV3_ALERT_HANDSHAKE_FAILURE',
}


def private_address(value):
    try:
        address = IPv4Address(value)
    except ValueError:
        raise argparse.ArgumentTypeError('RFC 1918 IPv4 address required') from None
    if not any(address in network for network in PRIVATE):
        raise argparse.ArgumentTypeError('RFC 1918 IPv4 address required')
    return str(address)


def send_empty_export(host, context):
    connection = HTTPSConnection(host, PORT, timeout=5, context=context)
    try:
        connection.request('POST', '/v1/traces', body=b'',
                                headers={'Content-Type': 'application/x-protobuf'})
        with connection.getresponse() as response:
            return response.status
    finally:
        connection.close()


def verify(host, client, no_client, send=send_empty_export):
    if send(host, client) != 200:
        return False, 'authenticated empty OTLP export was not accepted'
    try:
        send(host, no_client)
    except ssl.SSLError as error:
        if error.reason in CERTIFICATE_REQUIRED:
            return True, 'authenticated export accepted and client certificate required'
        return False, 'unexpected TLS failure without client certificate'
    return False, 'receiver responded without client certificate'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('host', type=private_address)
    parser.add_argument('--ca', required=True)
    parser.add_argument('--cert', required=True)
    parser.add_argument('--key', required=True)
    args = parser.parse_args()
    try:
        client = ssl.create_default_context(cafile=args.ca)
        client.load_cert_chain(args.cert, args.key)
        no_client = ssl.create_default_context(cafile=args.ca)
        passed, message = verify(args.host, client, no_client)
    except (OSError, ValueError):
        passed, message = False, 'could not complete the authenticated private OTLP request'
    print(message)
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
