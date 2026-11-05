#!/usr/bin/env python3
"""Allow only the ready Grafana Pod; reset to deny before resolving runtime identity."""
import ipaddress
import json
import subprocess

KUBE = ['kubectl', '-n', 'monitoring']
DENY = ['127.0.0.1/32']


def kube(*args):
    return subprocess.check_output(KUBE + list(args), text=True)


def patch_source(ranges):
    patch = {'spec': {'ipAllowList': {'sourceRange': ranges}}}
    kube('patch', 'middleware', 'mcp-prometheus-internal', '--type=merge', '-p', json.dumps(patch))


def ready_pod(pods):
    ready = [p for p in pods['items'] if not p['metadata'].get('deletionTimestamp')
             and p['status'].get('phase') == 'Running'
             and any(c['type'] == 'Ready' and c['status'] == 'True' for c in p['status'].get('conditions', []))]
    if len(ready) != 1:
        raise ValueError('Exactly one ready Grafana Pod is required')
    return ready[0]


def grafana_source(pods, node):
    pod = ready_pod(pods)
    if pod['spec']['nodeName'] != node['metadata']['name']:
        raise ValueError('Pod/node mismatch')
    address = ipaddress.ip_address(pod['status']['podIP'])
    network = ipaddress.ip_network(node['spec']['podCIDR'])
    # Reject the node/CNI gateway and non-Pod addresses, including loopback.
    if address.version != 4 or address not in network or int(address) <= int(network.network_address) + 1 or address == network.broadcast_address:
        raise ValueError('Expected a distinct IPv4 Pod address')
    return str(address) + '/32'


def main():
    if not kube('get', 'middleware', 'mcp-prometheus-internal', '--ignore-not-found', '-o', 'json').strip():
        print('MCP boundary not installed')
        return
    patch_source(DENY)
    pods = json.loads(kube('get', 'pods', '-l', 'app=grafana', '-o', 'json'))
    pod = ready_pod(pods)
    node = json.loads(kube('get', 'node', pod['spec']['nodeName'], '-o', 'json'))
    source = grafana_source(pods, node)
    patch_source([source])
    print('MCP boundary allows only the ready Grafana Pod /32')


if __name__ == '__main__':
    main()
