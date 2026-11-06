// 시나리오 1: 서로 다른 회원 N명이 같은 상품을 동시에 찜한다 → 모두 204, favoriteCount = N
// 회원 9100001~9100000+N, 상품 9000001
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, withHeaders, concurrency, favoriteCount, memberAuth, oneShotOptions, requireZero, startAt,
  startTogether } from '../lib/common.js';

const N = concurrency(50);
const PRODUCT_ID = 9000001;
const FIRST_MEMBER_ID = 9100001;

export const options = oneShotOptions(N, { 'http_req_failed{op:write}': ['rate==0'] });

export function setup() {
  requireZero('찜 수', favoriteCount(PRODUCT_ID));
  const members = [];
  for (let i = 0; i < N; i++) {
    members.push(memberAuth(FIRST_MEMBER_ID + i));
  }
  return { members, at: startAt() };
}

export default function (data) {
  const member = data.members[__VU - 1];
  startTogether(data.at);
  const res = http.put(`${BASE_URL}/api/v1/favorites/${PRODUCT_ID}`, null,
    { headers: withHeaders(member), tags: { op: 'write' } });
  check(res, { '찜 204': (r) => r.status === 204 });
}

export function teardown() {
  const actual = favoriteCount(PRODUCT_ID);
  console.log(`[결과] favoriteCount 기대=${N} 실제=${actual}`);
  check(actual, { [`favoriteCount = N(${N})`]: (v) => v === N });
}
