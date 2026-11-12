#!/usr/bin/env node
// 사용자 유형별 더미 데이터를 Gateway API로만 넣는다(#1289). 사용법은 같은 폴더의 README.md
import { parseArgs } from 'node:util';
import { createClient } from './lib/api.mjs';
import { loadManifest, manifestPath, saveManifest } from './lib/manifest.mjs';
import { PHASES } from './lib/phases.mjs';
import { SCALES, totals } from './lib/plan.mjs';

const DEV_CONFIRM_FLAG = 'yes-i-know-dev-is-production';

function fail(message) {
  console.error(`seed: ${message}`);
  process.exit(2);
}

const { values } = parseArgs({
  options: {
    env: { type: 'string' },
    scale: { type: 'string' },
    only: { type: 'string' },
    concurrency: { type: 'string', default: '8' },
    [DEV_CONFIRM_FLAG]: { type: 'boolean', default: false },
  },
});

const phaseNames = PHASES.map(([name]) => name);
if (!['local', 'dev'].includes(values.env)) fail('--env local|dev 가 필요하다');
if (!(values.scale in SCALES)) fail(`--scale ${Object.keys(SCALES).join('|')} 가 필요하다`);
if (values.only && !phaseNames.includes(values.only)) fail(`--only 는 ${phaseNames.join(' | ')} 중 하나다`);
const concurrency = Number(values.concurrency);
if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 64) fail('--concurrency 는 1~64');

function connection(env) {
  if (env === 'local') {
    return {
      baseUrl: process.env.SEED_BASE_URL ?? 'http://localhost:8083',
      admin: {
        email: process.env.SEED_ADMIN_EMAIL ?? 'admin@kidly.local',
        password: process.env.SEED_ADMIN_PASSWORD ?? 'local-admin-password',
      },
    };
  }
  // dev는 실제 운영이다. 확인 플래그와 접속 값을 모두 직접 줘야 돈다
  if (!values[DEV_CONFIRM_FLAG]) fail(`dev는 운영 서버다. 넣으려면 --${DEV_CONFIRM_FLAG} 를 붙인다`);
  const { SEED_BASE_URL, SEED_ADMIN_EMAIL, SEED_ADMIN_PASSWORD } = process.env;
  if (!SEED_BASE_URL || !SEED_ADMIN_EMAIL || !SEED_ADMIN_PASSWORD) {
    fail('dev는 SEED_BASE_URL · SEED_ADMIN_EMAIL · SEED_ADMIN_PASSWORD 환경 변수가 필요하다');
  }
  return { baseUrl: SEED_BASE_URL.replace(/\/$/, ''), admin: { email: SEED_ADMIN_EMAIL, password: SEED_ADMIN_PASSWORD } };
}

const { baseUrl, admin } = connection(values.env);
const manifest = loadManifest(values.env, values.scale, baseUrl);
const api = createClient({ baseUrl, admin });

let lastSave = 0;
const ctx = {
  api, manifest, concurrency, scale: values.scale,
  // 자주 부르므로 2초에 한 번만 파일에 쓴다. 단계가 끝나거나 멈출 때는 꼭 쓴다
  touch() {
    if (Date.now() - lastSave > 2000) {
      saveManifest(manifest);
      lastSave = Date.now();
    }
  },
};

process.on('SIGINT', () => {
  saveManifest(manifest);
  console.error('\nseed: 멈췄다. 기록을 저장했으니 같은 명령으로 이어서 돌린다');
  process.exit(130);
});

const goal = totals(values.scale);
console.log(`seed: env=${values.env} scale=${values.scale} ${baseUrl} 동시 ${concurrency}`);
console.log(`seed: 목표 구매자 ${goal.buyers} · 판매자 ${goal.sellers} · 자녀 ${goal.children} · 상품 ${goal.products} · 찜 ${goal.favorites} · 장바구니 ${goal.cartLines} · 주문 ${goal.orders}`);

const started = Date.now();
for (const [name, run] of PHASES) {
  if (values.only && values.only !== name) continue;
  const phaseStarted = Date.now();
  const requestsBefore = api.requests();
  try {
    const { total, created, skipped } = await run(ctx);
    saveManifest(manifest);
    const seconds = ((Date.now() - phaseStarted) / 1000).toFixed(1);
    console.log(`[${name}] 대상 ${total} · 처리 ${created} · 기록에 있어 건너뜀 ${skipped} · 요청 ${api.requests() - requestsBefore} · ${seconds}s`);
  } catch (error) {
    saveManifest(manifest);
    console.error(`[${name}] 실패: ${error.message}`);
    console.error(`seed: 기록은 ${manifestPath(values.env)}에 저장했다. 원인을 고친 뒤 같은 명령으로 이어서 돌린다`);
    process.exit(1);
  }
}
console.log(`seed: 끝 · 요청 ${api.requests()} · ${((Date.now() - started) / 1000).toFixed(1)}s · 기록 ${manifestPath(values.env)}`);
