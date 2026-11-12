#!/usr/bin/env node
// 기록(manifest)의 행이 Gateway API로 보이는지 확인한다. 읽기만 한다. 사용법은 README.md
import { parseArgs } from 'node:util';
import { readFileSync } from 'node:fs';
import { createClient } from './lib/api.mjs';
import { manifestPath } from './lib/manifest.mjs';
import { SCALES, STAGES, buyerKey, buyerPlan, childPlans, orderPlan, sellerKey, sellerPlan } from './lib/plan.mjs';

const { values } = parseArgs({ options: { env: { type: 'string', default: 'local' } } });
const manifest = JSON.parse(readFileSync(manifestPath(values.env), 'utf8'));
const api = createClient({ baseUrl: manifest.baseUrl });
const scale = SCALES[manifest.scale];
const range = (n) => Array.from({ length: n }, (_, i) => i);
const problems = [];
const check = (ok, message) => ok || problems.push(message);
const buyerToken = (i) => api.memberToken(manifest.members[buyerKey(i)], buyerPlan(i).nickname, ['BUYER']);

for (const j of range(scale.sellers)) {
  const token = await api.memberToken(manifest.members[sellerKey(j)], sellerPlan(j).nickname, ['BUYER', 'SELLER']);
  const seller = (await api.call('GET', '/api/v1/seller/me', { token })).body;
  check(seller.status === 'ACTIVE', `${sellerKey(j)} 판매자 상태 ${seller.status}`);
}

let children = 0;
for (const i of range(scale.buyers)) {
  const token = await buyerToken(i);
  const list = (await api.call('GET', '/api/v1/children', { token })).body;
  const names = new Set(list.map((c) => c.name));
  for (const child of childPlans(i)) check(names.has(child.name), `${buyerKey(i)} 자녀 ${child.name} 없음`);
  children += list.length;
}

const productStatus = {};
for (const [key, { productId }] of Object.entries(manifest.products)) {
  const product = (await api.call('GET', `/api/v1/products/${productId}`)).body;
  productStatus[product.status] = (productStatus[product.status] ?? 0) + 1;
  check(product.businessName, `${key} 판매자 상호 없음(판매자 복사본 지연)`);
}

const stageCounts = Object.fromEntries(STAGES.map((s) => [s, 0]));
const itemStatus = {};
const deliveryStatus = {};
for (const o of range(scale.buyers * scale.ordersPerBuyer)) {
  const plan = orderPlan(o, manifest.scale);
  const entry = manifest.orders[plan.key];
  const detail = (await api.call('GET', `/api/v1/orders/${entry.orderId}`, { token: await buyerToken(plan.buyerIndex) })).body;
  check(detail.status === 'PAID', `${plan.key} 주문 상태 ${detail.status}`);
  check(entry.stage === plan.target, `${plan.key} 기록 단계 ${entry.stage}, 목표 ${plan.target}`);
  stageCounts[entry.stage]++;
  detail.items.forEach((it) => (itemStatus[it.status] = (itemStatus[it.status] ?? 0) + 1));
  detail.deliveries.forEach((d) => (deliveryStatus[d.status] = (deliveryStatus[d.status] ?? 0) + 1));
}

const reviewed = new Set(Object.keys(manifest.reviews).map(Number));
const reviewProducts = new Set();
for (const o of range(scale.buyers * scale.ordersPerBuyer)) {
  const plan = orderPlan(o, manifest.scale);
  plan.reviews.forEach((r, n) => r && reviewProducts.add(manifest.products[`product-${String(plan.items[n].productIndex + 1).padStart(5, '0')}`].productId));
}
let visibleReviews = 0;
for (const productId of reviewProducts) {
  let cursor = null;
  do {
    const query = `productId=${productId}&size=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const { body } = await api.call('GET', `/api/v1/reviews?${query}`);
    visibleReviews += body.items.filter((r) => reviewed.has(Number(r.orderItemId))).length;
    cursor = body.nextCursor;
  } while (cursor);
}
check(visibleReviews === reviewed.size, `상품 리뷰 목록에 보이는 시드 리뷰 ${visibleReviews} / 기록 ${reviewed.size}`);

console.log(`판매자 ${scale.sellers} 확인 · 자녀 ${children}`);
console.log(`상품 상태 ${JSON.stringify(productStatus)}`);
console.log(`주문 단계 ${JSON.stringify(stageCounts)} · 주문상품 ${JSON.stringify(itemStatus)} · 배송 ${JSON.stringify(deliveryStatus)}`);
console.log(`리뷰 기록 ${reviewed.size} · 상품 리뷰 목록에 보임 ${visibleReviews}`);
if (problems.length) {
  console.error(`문제 ${problems.length}건:\n- ${problems.slice(0, 20).join('\n- ')}`);
  process.exit(1);
}
console.log('verify: 모두 맞다');
