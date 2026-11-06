// 시나리오 3: 같은 주문상품에 리뷰를 동시에 N번 쓴다 → 201 한 번, 나머지 N−1번은 409 REVIEW_ALREADY_WRITTEN
// 409는 기대한 결과라 오류율에서 빼고 따로 센다(review_created · review_conflict)
// 회원 9300001, 주문상품 9300001, 상품 9000003
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, allReviews, withHeaders, concurrency, memberAuth, oneShotOptions, requireZero, reviewSummary,
  startAt, startTogether } from '../lib/common.js';

const N = concurrency(50);
const PRODUCT_ID = 9000003;
const MEMBER_ID = 9300001;
const ORDER_ITEM_ID = '9300001';
// 409 응답(ProblemDetail)에는 오류 코드 이름이 없어 REVIEW_ALREADY_WRITTEN의 title로 본다
const ALREADY_WRITTEN_TITLE = '이미 리뷰를 쓴 주문상품입니다';

// 201이 한 번도 없으면 review_created 표본이 0이라 count threshold가 판단하지 못할 수 있다. 그 경우는 teardown의 reviewCount check가 잡는다
const created = new Counter('review_created');
const conflict = new Counter('review_conflict');

// 409를 실패(http_req_failed)로 세지 않는다
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 409));

export const options = oneShotOptions(N, {
  'http_req_failed{op:write}': ['rate==0'],
  review_created: ['count==1'],
  review_conflict: [`count==${N - 1}`],
});

export function setup() {
  requireZero('리뷰 수', reviewSummary(PRODUCT_ID).reviewCount);
  return { member: memberAuth(MEMBER_ID), at: startAt() };
}

export default function (data) {
  startTogether(data.at);
  const res = http.post(`${BASE_URL}/api/v1/reviews`,
    JSON.stringify({ orderItemId: ORDER_ITEM_ID, rating: 5, content: `동시 작성 ${__VU}` }),
    { headers: withHeaders(data.member, { 'Content-Type': 'application/json' }), tags: { op: 'write' } });
  if (res.status === 201) {
    created.add(1);
  } else if (res.status === 409) {
    conflict.add(1);
  }
  check(res, {
    '201 또는 409': (r) => r.status === 201 || r.status === 409,
    '409면 REVIEW_ALREADY_WRITTEN': (r) => r.status !== 409 || r.json('title') === ALREADY_WRITTEN_TITLE,
  });
}

export function teardown() {
  const summary = reviewSummary(PRODUCT_ID);
  const items = allReviews(PRODUCT_ID);
  console.log(`[결과] reviewCount 기대=1 실제=${summary.reviewCount}, 목록 기대=1 실제=${items.length}`);
  check(summary, { 'reviewCount = 1': (s) => s.reviewCount === 1 });
  check(items, { '목록 1건(주문상품 9300001)': (v) => v.length === 1 && v[0].orderItemId === ORDER_ITEM_ID });
}
