"""Verify Tempo profile selection without connecting to a cluster."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


MONITORING = Path(__file__).resolve().parents[1]


class TempoDeployTests(unittest.TestCase):
    def test_profile_selection(self):
        cases = (
            (False, False, "tempo"),
            (True, True, "tempo/private-receiver"),
            (True, False, None),
        )
        for enabled, has_secret, expected in cases:
            with self.subTest(enabled=enabled, has_secret=has_secret):
                self.check_deploy(enabled, has_secret, expected)

    def check_deploy(self, enabled, has_secret, expected):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copy(MONITORING / "deploy.sh", root)
            for name in ("bin", "operations", "secrets", "k8s"):
                (root / name).mkdir()
            (root / "secrets/GRAFANA_ADMIN_PASSWORD").write_text("fake")
            (root / "k8s/grafana.yaml").write_text("grafana-lokiexplore-app@1.2.3\n")
            if enabled:
                (root / "operations/tempo-private-receiver.enabled").touch()
            commands = {
                "kubectl": """case "$*" in
    '-n monitoring get secret tracing-tempo-server-tls -o name')
        test "$HAS_SECRET" = 1 ;;
    '-n monitoring get svc grafana'*) printf 127.0.0.1 ;;
    'apply --server-side --force-conflicts -k tempo'*)
        printf '%s\n' "${@: -1}" >> applied ;;
esac""",
                "curl": 'cat >/dev/null; printf \'{"info":{"version":"1.2.3"}}\'',
                "python3": """if [[ "$1" == -c ]]; then cat >/dev/null; printf 1.2.3; fi""",
            }
            for name, body in commands.items():
                path = root / "bin" / name
                path.write_text("#!/usr/bin/env bash\n" + body + "\n")
                path.chmod(0o755)
            result = subprocess.run(
                ["bash", str(root / "deploy.sh")],
                env={**os.environ, "PATH": f"{root / 'bin'}:{os.environ['PATH']}",
                     "HAS_SECRET": str(int(has_secret))},
                capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            applied = root / "applied"
            if expected is None:
                self.assertFalse(applied.exists())
                self.assertIn("현재 Tempo 배포를 유지", result.stdout)
            else:
                self.assertEqual(applied.read_text().splitlines(), [expected])
