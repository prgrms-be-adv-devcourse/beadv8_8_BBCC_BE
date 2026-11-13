import { mkdirSync, readFileSync, renameSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const MANIFEST_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', '.manifest');

export function manifestPath(env) {
  return join(MANIFEST_DIR, `${env}.json`);
}

// 만든 행의 ID 기록. 다시 돌릴 때 끝난 것을 건너뛰는 데와, 나중에 정리할 대상을 정하는 데 쓴다
export function loadManifest(env, scale, baseUrl) {
  const path = manifestPath(env);
  if (!existsSync(path)) {
    return {
      env, scale, baseUrl, createdAt: new Date().toISOString(),
      members: {}, sellerApplications: {}, addresses: {}, guardians: {}, children: {},
      products: {}, wallets: {}, favorites: {}, cartItems: {}, orders: {}, reviews: {},
    };
  }
  const manifest = JSON.parse(readFileSync(path, 'utf8'));
  if (manifest.scale !== scale || manifest.baseUrl !== baseUrl) {
    throw new Error(`${path}는 scale=${manifest.scale} · ${manifest.baseUrl}로 만든 기록이다. 같은 값으로 돌리거나 기록을 옮긴 뒤 돌린다`);
  }
  return manifest;
}

// 임시 파일에 쓰고 이름을 바꿔, 중간에 멈춰도 반쯤 쓴 기록이 남지 않는다
export function saveManifest(manifest) {
  const path = manifestPath(manifest.env);
  mkdirSync(dirname(path), { recursive: true });
  manifest.updatedAt = new Date().toISOString();
  writeFileSync(`${path}.tmp`, JSON.stringify(manifest, null, 1));
  renameSync(`${path}.tmp`, path);
}
