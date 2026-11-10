// 시나리오 공통: 설정 값 · 회원 인증 · 안 읽은 개수 요청
import http from 'k6/http';
import { check, fail } from 'k6';

// 회원 인증 방식(AUTH_MODE)
// - header(기본): notification-service에 직접 보내고 Gateway가 붙이던 회원 헤더(X-Member-*)를 붙인다. 로컬 전용
// - token: Gateway(8083)로 보내고 개발용 토큰 API(회원 서비스)로 받은 Bearer 토큰을 붙인다
export const AUTH_MODE = __ENV.AUTH_MODE || 'header';
if (AUTH_MODE !== 'header' && AUTH_MODE !== 'token') {
  throw new Error(`AUTH_MODE는 header 또는 token으로 주세요: ${AUTH_MODE}`);
}

// 요청을 보낼 주소. Docker로 실행하면 localhost 대신 host.docker.internal
export const BASE_URL = __ENV.BASE_URL || (AUTH_MODE === 'header' ? 'http://localhost:8090' : 'http://localhost:8083');

// 동시 사용자(VU) 수와 측정 시간. 단계별로 따로 실행한다(10 → 50 → 100 → 200)
export function loadOptions() {
  const vus = Number(__ENV.VUS || 10);
  if (!Number.isInteger(vus) || vus < 1) {
    throw new Error(`VUS는 1 이상 정수로 주세요: ${__ENV.VUS}`);
  }
  return {
    scenarios: {
      main: { executor: 'constant-vus', vus, duration: __ENV.DURATION || '30s' },
    },
    thresholds: {
      checks: ['rate==1'],
      'http_req_failed{op:count}': ['rate==0'],
      'http_req_duration{op:count}': ['p(95)<1000'],
      // 요약에 측정 대상 요청 수 · TPS가 따로 나오게 한다
      'http_reqs{op:count}': ['count>=1'],
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
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

// 안 읽은 개수를 묻고 시드가 넣은 개수와 맞는지 본다
export function countUnread(headers, expected) {
  const res = http.get(`${BASE_URL}/api/v1/notifications/unread-count`, { headers, tags: { op: 'count' } });
  check(res, {
    '200': (r) => r.status === 200,
    [`count = ${expected}`]: (r) => r.status === 200 && r.json('count') === expected,
  });
}
