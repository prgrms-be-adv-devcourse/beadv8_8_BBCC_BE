#!/usr/bin/env python3
"""Read an allowed Deployment JSON and print its tracing patch."""

import argparse
import json
import sys

from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'k3s'))
from tracing_agent import AGENT_JAR, AGENT_VOLUME, NAMESPACE, PILOT_SERVICES, agent_settings


def render_patch(deployment, java_options, rollback=False, search=False, service=None):
    metadata = deployment['metadata']
    deployment_service = metadata['name']
    if service not in (None, 'payment', 'chatbot') or (service and search):
        raise ValueError('unexpected tracing service selection')
    selected = 'search' if search else service
    allowed = (selected,) if selected else PILOT_SERVICES
    if (deployment['kind'] != 'Deployment' or metadata['namespace'] != NAMESPACE
            or deployment_service not in allowed):
        raise ValueError('unexpected Deployment for the selected tracing scope')

    service = deployment_service
    template = deployment['spec']['template']
    pod = template['spec']
    if selected:
        labels = template.get('metadata', {}).get('labels', {})
        if labels.get('app') != selected:
            raise ValueError('expected the selected service Pod label')
        if ((rollback and labels.get('kidly/tracing') != 'enabled')
                or (not rollback and 'kidly/tracing' in labels)):
            raise ValueError('unexpected selected service tracing label')
    containers = [c for c in pod['containers'] if c['name'] == service]
    if len(containers) != 1:
        raise ValueError('expected one application container matching the Deployment')
    container = containers[0]
    env = container.get('env', [])
    if container.get('envFrom') != [{'secretRef': {'name': f'{service}-env'}}]:
        raise ValueError('expected the existing deploy-generated environment Secret reference')
    patches, mount, volume = agent_settings(service, java_options)
    setting_names = {entry['name'] for entry in patches}
    existing_env = [entry for entry in env if entry['name'] in setting_names]
    existing_mounts = [entry for entry in container.get('volumeMounts', [])
                       if entry['name'] == AGENT_VOLUME or entry['mountPath'] == AGENT_JAR]
    existing_volumes = [entry for entry in pod.get('volumes', []) if entry['name'] == AGENT_VOLUME]
    if rollback:
        if (len(existing_env) != len(patches)
                or any(entry not in existing_env for entry in patches)
                or existing_mounts != [mount] or existing_volumes != [volume]):
            raise ValueError('live tracing settings do not match the generated deployment patch')
        patches = [{'name': name, '$patch': 'delete'} for name in sorted(setting_names)]
        mount = {'mountPath': AGENT_JAR, '$patch': 'delete'}
        volume = {'name': AGENT_VOLUME, '$patch': 'delete'}
    elif existing_env or existing_mounts or existing_volumes or any(
            entry['name'].startswith('OTEL_') for entry in env):
        raise ValueError('existing inline JVM or tracing settings require review')

    patch = {
        'metadata': {'resourceVersion': metadata['resourceVersion']},
        'spec': {'template': {'spec': {
            'containers': [{
                'name': service,
                'env': patches,
                'volumeMounts': [mount],
            }],
            'volumes': [volume],
        }}},
    }
    if selected:
        patch['spec']['template']['metadata'] = {
            'labels': {'kidly/tracing': None if rollback else 'enabled'},
        }
    return patch


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-tool-options', required=True, help='Current non-secret JVM flags')
    parser.add_argument('--rollback', action='store_true', help='Remove only the matching tracing patch')
    parser.add_argument('--search', action='store_true', help='Limit the patch to the search Deployment')
    parser.add_argument('--service', choices=('payment', 'chatbot'))
    args = parser.parse_args()
    try:
        print(json.dumps(render_patch(json.load(sys.stdin), args.java_tool_options, args.rollback, args.search, args.service), indent=2))
    except (KeyError, TypeError, ValueError) as error:
        sys.exit(f'Cannot generate tracing patch: {error}')
