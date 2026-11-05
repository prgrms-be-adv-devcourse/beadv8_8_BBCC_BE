#!/usr/bin/env python3
"""Server administrator CLI; not exposed by the restricted receiver or MCP."""
import argparse
import json
from pathlib import Path
from storage import Store, deliver_accepted


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('list-pending', 'retry-stored', 'discard-pending'))
    parser.add_argument('--context-id')
    args = parser.parse_args()
    store = Store(Path('/opt/app/monitoring/ai-notes-state'), Path('/opt/app/monitoring/operations/ai-notes.prom'))
    if args.action == 'list-pending':
        print(json.dumps(store.pending()))
        return
    if not args.context_id:
        parser.error('--context-id required')
    if args.action == 'retry-stored':
        print(json.dumps({'receipt': deliver_accepted(store, args.context_id)}))
    else:
        store.discard(args.context_id)
        print(json.dumps({'discarded': True}))


if __name__ == '__main__':
    main()
