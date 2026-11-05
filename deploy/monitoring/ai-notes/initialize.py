#!/usr/bin/env python3
"""Initialize only local state/telemetry, without issuing contexts or publishing."""
import os
from pathlib import Path
from storage import Store

if __name__ == '__main__':
    os.umask(0o077)
    Store(Path('/opt/app/monitoring/ai-notes-state'), Path('/opt/app/monitoring/operations/ai-notes.prom')).write_metrics()
