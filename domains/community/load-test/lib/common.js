// 시나리오 공통: 설정 값 · 개발용 토큰 · 동시 시작 · 확인 요청
import http from 'k6/http';
import { check, fail, sleep } from 'k6';

// 회원 인증 방식(AUTH_MODE)
// - header(기본): community-service(8092)에 직접 보내고 Gateway가 붙이던 회원 헤더(X-Member-*)를 붙인다. 로컬 전용
// - token: Gateway(8083)로 보내고 개발용 토큰 API(회원 서비스)로 받은 Bearer 토큰을 붙인다
export const AUTH_MODE = __ENV.AUTH_MODE || 'header';
if (AUTH_MODE !== 'header' && AUTH_MODE !== 'token') {
  throw new Error(`AUTH_MODE는 header 또는 token으로 주세요: ${AUTH_MODE}`);
}

// 요청을 보낼 주소. Docker로 실행하면 localhost 대신 host.docker.internal
export const BASE_URL = __ENV.BASE_URL || (AUTH_MODE === 'header' ? 'http://localhost:8092' : 'http://localhost:8083');

// 시드가 준비한 범위(seed.sql). 시나리오 4의 작성 자격이 200개라 N은 200까지
export const MAX_N = 200;

export function concurrency(defaultN) {
  const n = Number(__ENV.N || defaultN);
  if (!Number.isInteger(n) || n < 1 || n > MAX_N) {
    throw new Error(`N은 1~${MAX_N} 정수로 주세요: ${__ENV.N}`);
  }
  return n;
}

// VU N개가 한 번씩만 요청한다. 요청 시각은 setup이 정한 시작 시각에 맞춘다(startTogether)
export function oneShotOptions(n, thresholds) {
  return {
    scenarios: {
      main: { executor: 'per-vu-iterations', vus: n, iterations: 1, maxDuration: '60s' },
    },
    // checks: 요청 응답 · 최종 숫자 확인이 하나라도 틀리면 실패로 끝난다(종료 코드 99)
    // op:write: 측정 대상 요청만 따로 본다(setup의 토큰 발급 · 끝의 확인 요청은 뺀다)
    thresholds: Object.assign({
      checks: ['rate==1'],
      'http_req_duration{op:write}': ['p(95)<2000'],
      // 요약에 측정 대상 요청 수가 따로 나오게 한다
      'http_reqs{op:write}': ['count>=1'],
    }, thresholds),
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  };
}

// 회원 한 명의 인증 헤더. setup에서 회원마다 한 번 만든다
export function memberAuth(memberId) {
  const nickname = `부하${memberId}`;
  if (AUTH_MODE === 'header') {
    // Gateway가 토큰을 검증하고 붙이는 헤더와 같은 모양(shared MemberHeaders). 닉네임은 UTF-8 퍼센트 인코딩
    return {
      'X-Member-Id': String(memberId),
      'X-Member-Roles': 'BUYER',
      'X-Member-Nickname': encodeURIComponent(nickname),
    };
  }
  // 개발용 토큰 API(local · dev 프로필의 회원 서비스, Gateway /api/v1/dev/**)로 BUYER 토큰을 만든다
  const res = http.post(`${BASE_URL}/api/v1/dev/tokens`,
    JSON.stringify({ memberId, nickname, roles: ['BUYER'] }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'setup' } });
  if (!check(res, { '토큰 발급 200': (r) => r.status === 200 })) {
    fail(`토큰 발급 실패 memberId=${memberId} status=${res.status}`);
  }
  return { Authorization: `Bearer ${res.json('accessToken')}` };
}

export function withHeaders(memberHeaders, extraHeaders) {
  return { ...memberHeaders, ...(extraHeaders || {}) };
}

// 모든 VU가 같은 시각에 요청을 보내도록 setup이 정한 시각까지 기다린다(VU 시작 차이를 줄인다)
export function startAt(delayMs) {
  return Date.now() + (delayMs || 3000);
}

export function startTogether(at) {
  const wait = at - Date.now();
  if (wait > 0) {
    sleep(wait / 1000);
  }
}

export function getJson(path) {
  const res = http.get(`${BASE_URL}${path}`, { tags: { op: 'verify' } });
  if (res.status !== 200) {
    fail(`확인 요청 실패 ${path} status=${res.status}`);
  }
  return res.json();
}

export function favoriteCount(productId) {
  return getJson(`/api/v1/favorites/summary?productId=${productId}`).favoriteCount;
}

export function reviewSummary(productId) {
  return getJson(`/api/v1/reviews/summary?productId=${productId}`);
}

// 상품 리뷰 목록을 끝까지 읽는다(최신순 커서, 한 쪽 50건)
export function allReviews(productId) {
  const items = [];
  let cursor = null;
  do {
    const page = getJson(`/api/v1/reviews?productId=${productId}&size=50`
      + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''));
    items.push(...page.items);
    cursor = page.nextCursor;
  } while (cursor);
  return items;
}

// 시작 전 상태가 비어 있지 않으면 측정하지 않는다(cleanup.sql · seed.sql을 먼저 돌린다)
export function requireZero(name, actual) {
  if (actual !== 0) {
    fail(`${name}이(가) 0이 아니에요(${actual}). cleanup.sql → seed.sql을 먼저 돌려 주세요`);
  }
}
