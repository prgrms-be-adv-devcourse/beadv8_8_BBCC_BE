#!/usr/bin/env python3
"""Print the manual OTLP route resources for one cluster. Does not apply them."""

import argparse
from ipaddress import IPv4Address, IPv4Network
import json

NODE_PORT = 30438
OTLP_PORT = 4318
PRIVATE_NETWORKS = tuple(map(IPv4Network, ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))


def private_ipv4(value):
    try:
        address = IPv4Address(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError('expected a private IPv4 address') from error
    if not any(address in network for network in PRIVATE_NETWORKS):
        raise argparse.ArgumentTypeError('expected an RFC 1918 IPv4 address')
    return address


def monitoring_resources(source):
    selector = {'app': 'tempo'}
    return [
        {
            'apiVersion': 'v1',
            'kind': 'Service',
            'metadata': {'name': 'tempo-otlp-private', 'namespace': 'monitoring'},
            'spec': {
                'type': 'NodePort',
                'externalTrafficPolicy': 'Local',
                'selector': selector,
                'ports': [{'name': 'otlp-mtls', 'port': OTLP_PORT,
                           'targetPort': OTLP_PORT, 'nodePort': NODE_PORT}],
            },
        },
        {
            'apiVersion': 'networking.k8s.io/v1',
            'kind': 'NetworkPolicy',
            'metadata': {'name': 'tempo-otlp-private', 'namespace': 'monitoring'},
            'spec': {
                'podSelector': {'matchLabels': selector},
                'policyTypes': ['Ingress'],
                'ingress': [{
                    'from': [{'ipBlock': {'cidr': f'{source}/32'}}],
                    'ports': [{'protocol': 'TCP', 'port': OTLP_PORT}],
                }],
            },
        },
    ]


def app_resources(destination):
    return [{
        'apiVersion': 'v1',
        'kind': 'ConfigMap',
        'metadata': {'name': 'tracing-tempo-endpoint', 'namespace': 'kidly-dev'},
        'data': {'url': f'https://{destination}:{NODE_PORT}'},
    }]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    clusters = parser.add_subparsers(dest='cluster', required=True)
    monitoring = clusters.add_parser('monitoring')
    monitoring.add_argument('--app-source', required=True, type=private_ipv4)
    app = clusters.add_parser('app')
    app.add_argument('--tempo-node', required=True, type=private_ipv4)
    args = parser.parse_args()
    items = (monitoring_resources(args.app_source) if args.cluster == 'monitoring'
             else app_resources(args.tempo_node))
    print(json.dumps({'apiVersion': 'v1', 'kind': 'List', 'items': items}, indent=2))


if __name__ == '__main__':
    main()
