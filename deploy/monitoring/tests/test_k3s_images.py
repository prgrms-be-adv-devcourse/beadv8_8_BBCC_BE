"""deploy/k3s/images.py: k3s 에 쌓인 배포 이미지 중 지울 것을 고르는 규칙. 서버 · 클러스터 없이 돈다"""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'k3s' / 'images.py'
spec = importlib.util.spec_from_file_location('images', SCRIPT)
images = importlib.util.module_from_spec(spec)
spec.loader.exec_module(images)

REPO = 'ghcr.io/team/kidly-recommend-service'
OTHER = 'ghcr.io/team/kidly-order-service'


def image(image_id, repo, created):
    """crictl inspecti -o json 이 이미지 하나마다 내는 모양"""
    return {'status': {'id': image_id, 'repoTags': [], 'repoDigests': [repo + '@sha256:' + image_id]},
            'info': {'imageSpec': {'created': created}}}


class StaleImagesTests(unittest.TestCase):
    def test_keeps_three_newest_per_repository(self):
        inspected = [image('a%d' % day, REPO, '2026-11-%02dT00:00:00Z' % day) for day in range(1, 6)]
        self.assertEqual(images.stale_images(inspected, [REPO], set()), ['a2', 'a1'])

    def test_keeps_images_k3s_still_uses_even_if_old(self):
        inspected = [image('a%d' % day, REPO, '2026-11-%02dT00:00:00Z' % day) for day in range(1, 6)]
        self.assertEqual(images.stale_images(inspected, [REPO], {REPO + '@sha256:a1'}), ['a2'])

    def test_leaves_repositories_not_deployed_by_this_script(self):
        inspected = [image('k%d' % day, 'docker.io/danielqsj/kafka-exporter', '2026-11-%02dT00:00:00Z' % day)
                     for day in range(1, 6)]
        self.assertEqual(images.stale_images(inspected, [REPO], set()), [])

    def test_counts_three_newest_separately_for_each_repository(self):
        inspected = [image('r%d' % day, REPO, '2026-11-%02dT00:00:00Z' % day) for day in range(1, 5)]
        inspected += [image('o%d' % day, OTHER, '2026-11-%02dT00:00:00Z' % day) for day in range(1, 4)]
        self.assertEqual(images.stale_images(inspected, [REPO, OTHER], set()), ['r1'])

    def test_reads_repository_from_tag_when_image_has_no_digest(self):
        inspected = [{'status': {'id': 't%d' % day, 'repoTags': [REPO + ':v%d' % day], 'repoDigests': []},
                      'info': {'imageSpec': {'created': '2026-11-%02dT00:00:00Z' % day}}} for day in range(1, 5)]
        self.assertEqual(images.stale_images(inspected, [REPO], set()), ['t1'])


class ImagesInUseTests(unittest.TestCase):
    def test_collects_images_from_pods_deployments_and_cronjobs(self):
        cluster = {'items': [
            {'kind': 'Pod', 'spec': {'containers': [{'image': REPO + '@sha256:a'}]},
             'status': {'containerStatuses': [{'image': REPO + '@sha256:a', 'imageID': REPO + '@sha256:a'}]}},
            {'kind': 'Deployment', 'spec': {'template': {'spec': {'containers': [{'image': REPO + '@sha256:b'}]}}}},
            {'kind': 'CronJob', 'spec': {'jobTemplate': {'spec': {'template': {'spec': {
                'initContainers': [{'image': OTHER + '@sha256:c'}], 'containers': [{'image': OTHER + '@sha256:d'}]}}}}}},
        ]}
        self.assertEqual(images.images_in_use(cluster),
                         {REPO + '@sha256:a', REPO + '@sha256:b', OTHER + '@sha256:c', OTHER + '@sha256:d'})


class CommandTests(unittest.TestCase):
    def test_prints_ids_to_remove_one_per_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            inspected = Path(tmp) / 'images.json'
            cluster = Path(tmp) / 'cluster.json'
            inspected.write_text(json.dumps([image('a%d' % day, REPO, '2026-11-%02dT00:00:00Z' % day)
                                             for day in range(1, 6)]))
            cluster.write_text(json.dumps({'items': []}))
            result = subprocess.run([sys.executable, str(SCRIPT), 'stale', str(inspected), str(cluster),
                                     REPO + '@sha256:a5'], capture_output=True, text=True, check=True)
        self.assertEqual(result.stdout.split(), ['a2', 'a1'])

    def test_accepts_single_image_object_from_inspecti(self):
        with tempfile.TemporaryDirectory() as tmp:
            inspected = Path(tmp) / 'images.json'
            cluster = Path(tmp) / 'cluster.json'
            inspected.write_text(json.dumps(image('a1', REPO, '2026-11-01T00:00:00Z')))
            cluster.write_text(json.dumps({'items': []}))
            result = subprocess.run([sys.executable, str(SCRIPT), 'stale', str(inspected), str(cluster), REPO],
                                    capture_output=True, text=True, check=True)
        self.assertEqual(result.stdout, '')


if __name__ == '__main__':
    unittest.main()
