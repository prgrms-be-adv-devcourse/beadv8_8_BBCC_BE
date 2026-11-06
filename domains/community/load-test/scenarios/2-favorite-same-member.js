// 시나리오 2: 같은 회원이 같은 상품을 동시에 N번 찜한다 → 모두 204, favoriteCount = 1, 내 찜 1건
// 회원 9200001, 상품 9000002
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, withHeaders, concurrency, favoriteCount, memberAuth, oneShotOptions, requireZero, startAt,
  startTogether } from '../lib/common.js';

const N = concurrency(50);
const PRODUCT_ID = 9000002;
const MEMBER_ID = 9200001;

export const options = oneShotOptions(N, { 'http_req_failed{op:write}': ['rate==0'] });

export function setup() {
  requireZero('찜 수', favoriteCount(PRODUCT_ID));
  return { member: memberAuth(MEMBER_ID), at: startAt() };
}

export default function (data) {
  startTogether(data.at);
  const res = http.put(`${BASE_URL}/api/v1/favorites/${PRODUCT_ID}`, null,
    { headers: withHeaders(data.member), tags: { op: 'write' } });
  check(res, { '찜 204(이미 찜했어도 204)': (r) => r.status === 204 });
}

export function teardown(data) {
  const actual = favoriteCount(PRODUCT_ID);
  const mine = http.get(`${BASE_URL}/api/v1/favorites?productIds=${PRODUCT_ID}`,
    { headers: withHeaders(data.member), tags: { op: 'verify' } });
  const mineCount = mine.status === 200 ? mine.json('items').length : -1;
  console.log(`[결과] favoriteCount 기대=1 실제=${actual}, 내 찜 기대=1 실제=${mineCount}`);
  check(actual, { 'favoriteCount = 1': (v) => v === 1 });
  check(mineCount, { '내 찜 1건': (v) => v === 1 });
}
