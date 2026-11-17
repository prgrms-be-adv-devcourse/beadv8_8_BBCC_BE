// 서버 처리 한계 측정(#1471). 구매자 화면을 정해진 속도(RATE, 화면/초)로 열어, 그 속도에서 SLO(p95 500ms 이하, 오류율 1% 미만)를 지키는지 본다.
// 속도마다 따로 실행해(run.sh) SLO를 처음 넘는 속도를 찾는다. 실행 방법 · 한계는 README
import http from 'k6/http';
import { fail, sleep } from 'k6';
import { pickScreen, SCREENS } from './lib/screens.js';

const API = `${__ENV.BASE_URL || 'http://localhost:8083'}/api/v1`;
const RATE = Number(__ENV.RATE || 5);
const USERS = Number(__ENV.USERS || 100);
const ORDERS = (__ENV.ORDERS || 'on') !== 'off';
const CATALOG_PAGES = 5;
const DETAILED_PRODUCTS = 40;
const MIN_BALANCE = 300000;
const CHARGE = 1000000;

if (!Number.isFinite(RATE) || RATE <= 0) {
  throw new Error(`RATE는 0보다 큰 수로 주세요(초당 화면 수): ${__ENV.RATE}`);
}
if (!Number.isInteger(USERS) || USERS < 1) {
  throw new Error(`USERS는 1 이상 정수로 주세요: ${__ENV.USERS}`);
}

// 열린 모델: 서버가 느려져도 화면을 여는 속도는 그대로다. VU 수로 거는 닫힌 모델은 느려지면 요청도 줄어 과부하가 가려진다.
// 예열 구간은 JIT · 커넥션 풀 · 캐시가 데워지는 동안이라 SLO 판정에서 뺀다
const arrival = {
  executor: 'constant-arrival-rate',
  rate: RATE,
  timeUnit: '1s',
  preAllocatedVUs: Math.ceil(RATE * 2),
  maxVUs: Number(__ENV.MAX_VUS || Math.max(50, Math.ceil(RATE * 20))),
};
const WARMUP = __ENV.WARMUP || '30s';
const DURATION = __ENV.DURATION || '3m';

export const options = {
  scenarios: {
    warmup: Object.assign({}, arrival, { duration: WARMUP, tags: { phase: 'warmup' } }),
    measure: Object.assign({}, arrival, { startTime: WARMUP, duration: DURATION, tags: { phase: 'measure' } }),
  },
  thresholds: {
    'http_req_duration{phase:measure}': ['p(95)<500'],
    'http_req_failed{phase:measure}': ['rate<0.01'],
    // 0보다 크면 k6가 정한 속도로 화면을 열지 못했다. 서버가 느려 VU가 모자랐거나 부하 발생기가 병목이다
    'dropped_iterations{scenario:measure}': ['count==0'],
    // 요약에 측정 구간 요청 수가 따로 나오게 한다
    'http_reqs{phase:measure}': ['count>0'],
  },
  setupTimeout: '10m',
  teardownTimeout: '10m',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function json(method, path, body, auth) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, auth);
  return http.request(method, API + path, body === undefined ? null : JSON.stringify(body), { headers, tags: { phase: 'setup' } });
}

function expectOk(res, what) {
  if (res.status < 200 || res.status >= 300) {
    fail(`${what} 실패 status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
  return res;
}

// 개발용 API(local · dev 프로필)로 더미 회원과 BUYER 토큰을 만든다. key가 같으면 같은 회원이라 다시 돌려도 회원이 늘지 않는다.
// 토큰은 30분 동안 쓸 수 있으므로 한 번 실행은 예열 포함 30분 안에 끝나야 한다
function createUsers() {
  const users = [];
  for (let i = 1; i <= USERS; i++) {
    const nickname = `부하테스트${i}`;
    const member = expectOk(json('POST', '/dev/members', { key: `load-capacity-${i}`, nickname }), '더미 회원 만들기');
    const token = expectOk(json('POST', '/dev/tokens', { memberId: member.json('memberId'), nickname, roles: ['BUYER'] }), '토큰 발급');
    users.push({ auth: { Authorization: `Bearer ${token.json('accessToken')}` } });
  }
  return users;
}

function leafCategoryIds(nodes) {
  return nodes.flatMap((node) => (node.leaf ? [node.categoryId] : leafCategoryIds(node.children || [])));
}

// 보낼 상품 · 카테고리 · 옵션을 서버에서 읽어 온다. 시드 없이 그 환경에 있는 판매 중 상품을 쓴다
function loadCatalog() {
  const categoryIds = leafCategoryIds(expectOk(json('GET', '/categories'), '카테고리 조회').json());
  const productIds = [];
  let pages = 0;
  for (let page = 0; page < CATALOG_PAGES; page++) {
    const content = expectOk(json('GET', `/products?page=${page}&size=20`), '상품 목록 조회').json('content') || [];
    if (content.length === 0) {
      break;
    }
    pages++;
    content.forEach((item) => productIds.push(item.productId));
  }
  if (productIds.length === 0 || categoryIds.length === 0) {
    fail('판매 중 상품이나 카테고리가 없어 측정할 수 없습니다');
  }
  // 재고가 넉넉한(AVAILABLE) 옵션만 담기 · 주문에 쓴다. LOW는 측정 중 품절되면 주문이 실패로 섞인다
  const options = [];
  productIds.slice(0, DETAILED_PRODUCTS).forEach((productId) => {
    const product = expectOk(json('GET', `/products/${productId}`), '상품 상세 조회').json();
    (product.options || [])
      .filter((option) => option.purchasable && option.stockStatus === 'AVAILABLE')
      .forEach((option) => options.push({ optionId: option.optionId, price: option.price }));
  });
  if (options.length === 0) {
    fail('재고가 넉넉한 옵션이 없어 담기 · 주문을 보낼 수 없습니다');
  }
  return { categoryIds, productIds, pages, options };
}

// 주문은 예치금으로 결제된다. 개발용 충전(토스 없음)으로 잔액이 모자란 회원만 채운다
function fundWallets(users) {
  users.forEach((user) => {
    const balance = expectOk(json('GET', '/wallets/me', undefined, user.auth), '잔액 조회').json('balance');
    if (balance < MIN_BALANCE) {
      expectOk(json('POST', '/dev/wallets/me/charges', { amount: CHARGE }, user.auth), '개발용 충전');
    }
  });
}

export function setup() {
  const users = createUsers();
  const catalog = loadCatalog();
  if (ORDERS) {
    fundWallets(users);
  }
  console.log(`준비 완료: 회원 ${users.length}명, 상품 ${catalog.productIds.length}개, 옵션 ${catalog.options.length}개, `
    + `화면 비중 ${SCREENS.filter((s) => s.weight > 0).map((s) => `${s.name}=${s.weight}`).join(' ')}`);
  return { users, catalog };
}

export default function (data) {
  pickScreen().run(data);
}

// 측정 주문이 dev 상품의 재고를 계속 잡고 있지 않게, 측정 회원의 결제 대기 · 결제 완료 주문을 모두 취소한다(취소하면 재고가 돌아온다)
export function teardown(data) {
  if (!ORDERS) {
    return;
  }
  sleep(10);
  let cancelled = 0;
  data.users.forEach((user) => {
    ['PENDING_PAYMENT', 'PAID'].forEach((status) => {
      const res = json('GET', `/orders?page=0&size=100&status=${status}`, undefined, user.auth);
      ((res.status === 200 && res.json('content')) || []).forEach((order) => {
        if (json('POST', `/orders/${order.orderId}/cancel`, undefined, user.auth).status === 200) {
          cancelled++;
        }
      });
    });
  });
  console.log(`측정 주문 ${cancelled}건 취소`);
}

function seconds(duration) {
  const match = /^(\d+)(s|m)$/.exec(duration);
  if (!match) {
    throw new Error(`DURATION은 30s · 3m 같은 초 · 분 단위로 주세요: ${duration}`);
  }
  return Number(match[1]) * (match[2] === 'm' ? 60 : 1);
}

function metric(data, name, stat) {
  const found = data.metrics[name];
  return found ? found.values[stat] : undefined;
}

// 속도마다 한 줄로 남겨 run.sh가 표로 모은다. setup_data에는 토큰이 있어 결과 파일에 남기지 않는다
export function handleSummary(data) {
  delete data.setup_data;
  const p95 = metric(data, 'http_req_duration{phase:measure}', 'p(95)');
  const failed = metric(data, 'http_req_failed{phase:measure}', 'rate');
  const dropped = metric(data, 'dropped_iterations{scenario:measure}', 'count') || 0;
  const requests = metric(data, 'http_reqs{phase:measure}', 'count') || 0;
  const line = {
    rate: RATE,
    rps: Math.round(requests / seconds(DURATION)),
    p95: p95 === undefined ? undefined : Math.round(p95),
    failed,
    dropped,
    slo: p95 !== undefined && p95 < 500 && failed !== undefined && failed < 0.01 && dropped === 0,
  };
  return {
    stdout: `\n${JSON.stringify(line)}\n`,
    [__ENV.SUMMARY_FILE || 'summary.json']: JSON.stringify(Object.assign({ line }, data), null, 2),
  };
}
