// 추천 HPA 확인용 부하(#1028). Gateway 를 거쳐 dev 추천 API 를 부른다
// 실행은 README 의 "스케일링 확인". TOKEN(dev 회원 액세스 토큰) · CHILD_ID 는 환경 변수로만 받는다
import http from 'k6/http';
import { check, sleep } from 'k6';

const base = __ENV.BASE_URL || 'https://dev-api.kidly.kro.kr';
const headers = { Authorization: `Bearer ${__ENV.TOKEN}` };

// 1분에 걸쳐 올리고 5분 유지해 HPA 가 늘리는 것을 보고, 내린 뒤 5분 넘게 줄어드는 것은 watch 로 본다
export const options = {
  stages: [
    { duration: '1m', target: Number(__ENV.VUS || 30) },
    { duration: '5m', target: Number(__ENV.VUS || 30) },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (!__ENV.TOKEN || !__ENV.CHILD_ID) {
    throw new Error('TOKEN 과 CHILD_ID 환경 변수가 필요합니다');
  }
}

export default function () {
  const page = Math.floor(Math.random() * 3);
  const res = http.get(`${base}/api/v1/recommendations/products?childId=${__ENV.CHILD_ID}&page=${page}&size=20`, {
    headers,
    tags: { name: 'recommendations/products' },
  });
  check(res, { '200': (r) => r.status === 200 });
  sleep(0.2);
}
