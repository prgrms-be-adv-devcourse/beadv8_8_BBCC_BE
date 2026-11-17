// 구매자 화면 하나를 열 때 FE가 보내는 요청 묶음. 같이 나가는 요청은 http.batch로, 앞 응답을 기다리는 요청은 그 뒤에 보낸다.
// 근거는 FE develop의 화면 · 쿼리 코드다(README의 "화면과 요청"). 외부 유료 · 외부 시스템을 부르는 요청은 뺐다
import http from 'k6/http';
import { sleep } from 'k6';

const API = `${__ENV.BASE_URL || 'http://localhost:8083'}/api/v1`;
const ANON_RATIO = Number(__ENV.ANON_RATIO || 0.3);
const ORDERS = (__ENV.ORDERS || 'on') !== 'off';

// 화면 비중(%). 기획서에 사용자 행동 수치가 없어 이커머스 깔때기(목록 → 상세 → 장바구니 → 주문)를 가정한 값이다
export const SCREENS = [
  { name: 'entry', weight: 10, run: entry },
  { name: 'list', weight: 15, run: list },
  { name: 'category', weight: 15, run: category },
  { name: 'detail', weight: 28, run: detail },
  { name: 'add-to-cart', weight: 6, run: addToCart },
  { name: 'cart', weight: 8, run: cart },
  { name: 'favorite', weight: 5, run: favorite },
  { name: 'orders', weight: 5, run: orders },
  { name: 'my', weight: 4, run: my },
  { name: 'notifications', weight: 2, run: notifications },
  { name: 'place-order', weight: ORDERS ? 2 : 0, run: placeOrder },
];

const TOTAL_WEIGHT = SCREENS.reduce((sum, screen) => sum + screen.weight, 0);

export function pickScreen() {
  let roll = Math.random() * TOTAL_WEIGHT;
  for (const screen of SCREENS) {
    roll -= screen.weight;
    if (roll < 0) {
      return screen;
    }
  }
  return SCREENS[0];
}

export function get(path, name, auth) {
  return { method: 'GET', url: API + path, params: { headers: auth || {}, tags: { name } } };
}

function send(method, path, name, auth, body, extraHeaders) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, auth, extraHeaders);
  return http.request(method, API + path, body === undefined ? null : JSON.stringify(body), { headers, tags: { name } });
}

function pick(items) {
  return items[Math.floor(Math.random() * items.length)];
}

// 비로그인으로도 열 수 있는 화면은 ANON_RATIO만큼 토큰 없이 연다(Gateway는 IP로 센다)
function viewer(data) {
  return Math.random() < ANON_RATIO ? null : pick(data.users).auth;
}

function productIdsOf(res) {
  try {
    return (res.json('content') || []).map((item) => item.productId);
  } catch (e) {
    return [];
  }
}

function favoritesAmong(res, auth) {
  const ids = productIdsOf(res);
  if (auth && ids.length > 0) {
    http.get(`${API}/favorites?productIds=${ids.join(',')}`, { headers: auth, tags: { name: 'GET /favorites?productIds' } });
  }
}

// 새로 들어온 방문: 세션 복원(/members/me) → 레이아웃 · 홈 요청 → 목록 상품의 찜 여부.
// 세션 복원의 /auth/reissue는 refresh 쿠키가 필요해 빼고, 알림 SSE도 장기 연결이라 뺐다(README의 한계)
function entry(data) {
  const auth = viewer(data);
  const requests = [
    get('/products?page=0&size=20', 'GET /products'),
    get('/products?page=0&size=20', 'GET /products'),
    get('/categories', 'GET /categories'),
  ];
  if (auth) {
    http.get(`${API}/members/me`, { headers: auth, tags: { name: 'GET /members/me' } });
    requests.push(get('/children', 'GET /children', auth));
    requests.push(get('/notifications/unread-count', 'GET /notifications/unread-count', auth));
  }
  const responses = http.batch(requests);
  favoritesAmong(responses[0], auth);
}

// 홈 목록 쪽 넘김 · 정렬 바꾸기
function list(data) {
  const auth = viewer(data);
  const sort = pick(['', '&sort=PRICE_ASC', '&sort=PRICE_DESC']);
  const page = Math.floor(Math.random() * data.catalog.pages);
  const res = http.get(`${API}/products?page=${page}&size=20${sort}`, { headers: auth || {}, tags: { name: 'GET /products' } });
  favoritesAmong(res, auth);
}

// 카테고리 칩을 눌러 거른 목록
function category(data) {
  const auth = viewer(data);
  const categoryId = pick(data.catalog.categoryIds);
  const res = http.get(`${API}/products?categoryId=${categoryId}&page=0&size=20`,
    { headers: auth || {}, tags: { name: 'GET /products?categoryId' } });
  favoritesAmong(res, auth);
}

// 상품 상세: 상세 · 찜 수 · (로그인이면) 찜 여부 · 자녀 · 조회 기록이 같이 나가고, 리뷰는 상세가 그려진 뒤에 나간다.
// 자녀를 고르지 않은 진입이라 사이즈 추천 · 함께 사면(OpenAI)은 부르지 않는다
function detail(data) {
  const auth = viewer(data);
  const productId = pick(data.catalog.productIds);
  const requests = [
    get(`/products/${productId}`, 'GET /products/{id}'),
    get(`/favorites/summary?productId=${productId}`, 'GET /favorites/summary'),
  ];
  if (auth) {
    requests.push(get('/children', 'GET /children', auth));
    requests.push(get(`/favorites?productIds=${productId}`, 'GET /favorites?productIds', auth));
    requests.push({
      method: 'POST', url: `${API}/recommendations/product-views`,
      body: JSON.stringify({ productId, viewType: 'DETAIL' }),
      params: { headers: Object.assign({ 'Content-Type': 'application/json' }, auth), tags: { name: 'POST /recommendations/product-views' } },
    });
  }
  http.batch(requests);
  http.batch([
    get(`/reviews/summary?productId=${productId}`, 'GET /reviews/summary'),
    get(`/reviews?productId=${productId}&size=10`, 'GET /reviews'),
  ]);
}

// 장바구니 담기 뒤 FE는 장바구니를 다시 읽는다
function addToCart(data) {
  const user = pick(data.users);
  const option = pick(data.catalog.options);
  send('POST', '/cart/items', 'POST /cart/items', user.auth, { optionId: Number(option.optionId), quantity: 1 });
  http.get(`${API}/cart`, { headers: user.auth, tags: { name: 'GET /cart' } });
}

const CART_LINES_KEPT = 5;

// 장바구니(= 주문서) 화면. 담기만 쌓이면 수량 상한(99)에 닿으므로, 줄이 많으면 하나를 빼는 편집도 여기서 한다
function cart(data) {
  const user = pick(data.users);
  const responses = http.batch(cartPage(user.auth));
  let lines = [];
  try {
    lines = responses[0].json('items') || [];
  } catch (e) {
    lines = [];
  }
  if (lines.length > CART_LINES_KEPT) {
    send('DELETE', `/cart/items/${lines[0].cartItemId}`, 'DELETE /cart/items/{id}', user.auth);
    http.get(`${API}/cart`, { headers: user.auth, tags: { name: 'GET /cart' } });
  }
}

function cartPage(auth) {
  return [
    get('/cart', 'GET /cart', auth),
    get('/members/me/addresses', 'GET /members/me/addresses', auth),
    get('/wallets/me', 'GET /wallets/me', auth),
    get('/children', 'GET /children', auth),
  ];
}

// 찜 누르기 · 풀기. FE는 바꾼 뒤 찜 쿼리를 모두 무효화해 찜 수 · 찜 여부를 다시 읽는다
function favorite(data) {
  const user = pick(data.users);
  const productId = pick(data.catalog.productIds);
  const method = Math.random() < 0.5 ? 'PUT' : 'DELETE';
  send(method, `/favorites/${productId}`, `${method} /favorites/{id}`, user.auth);
  http.batch([
    get(`/favorites/summary?productId=${productId}`, 'GET /favorites/summary'),
    get(`/favorites?productIds=${productId}`, 'GET /favorites?productIds', user.auth),
  ]);
}

function orderCounts(auth) {
  return [
    get('/orders?page=0&size=1', 'GET /orders?size=1', auth),
    get('/orders?page=0&size=1&status=PENDING_PAYMENT', 'GET /orders?size=1', auth),
    get('/orders?page=0&size=1&status=PAID', 'GET /orders?size=1', auth),
    get('/orders?page=0&size=1&status=CANCELLED', 'GET /orders?size=1', auth),
  ];
}

// 주문 내역: 목록과 탭 건수 4개가 같이 나간다
function orders(data) {
  const user = pick(data.users);
  http.batch([get('/orders?page=0&size=20', 'GET /orders', user.auth)].concat(orderCounts(user.auth)));
}

function my(data) {
  const user = pick(data.users);
  http.batch([
    get('/children', 'GET /children', user.auth),
    get('/members/me/addresses', 'GET /members/me/addresses', user.auth),
    get('/wallets/me', 'GET /wallets/me', user.auth),
    get('/payments?page=0&size=20', 'GET /payments', user.auth),
    get('/wish/requests', 'GET /wish/requests', user.auth),
  ].concat(orderCounts(user.auth)));
}

function notifications(data) {
  const user = pick(data.users);
  http.get(`${API}/notifications?page=0&size=20`, { headers: user.auth, tags: { name: 'GET /notifications' } });
}

const PAYMENT_POLLS = 10;

// 주문서에서 예치금으로 주문하고, 결제(Kafka로 비동기)가 끝날 때까지 FE처럼 1초마다 주문을 읽는다
function placeOrder(data) {
  const user = pick(data.users);
  const option = pick(data.catalog.options);
  http.batch(cartPage(user.auth));
  const res = send('POST', '/orders', 'POST /orders', user.auth, {
    items: [{ optionId: Number(option.optionId), quantity: 1, unitPrice: option.price }],
    shippingAddress: { recipientName: '부하테스트', phone: '010-0000-0000', zipCode: '06236', address: '서울시 강남구 테헤란로 1' },
  }, { 'Idempotency-Key': `load-${__VU}-${__ITER}-${Date.now()}` });
  if (res.status !== 201) {
    return;
  }
  const orderId = res.json('orderId');
  for (let i = 0; i < PAYMENT_POLLS; i++) {
    sleep(1);
    const order = http.get(`${API}/orders/${orderId}`, { headers: user.auth, tags: { name: 'GET /orders/{id}' } });
    if (order.status !== 200 || order.json('status') !== 'PENDING_PAYMENT') {
      break;
    }
  }
  http.batch([
    get(`/orders/${orderId}/timeline`, 'GET /orders/{id}/timeline', user.auth),
    get(`/payments?orderId=${orderId}`, 'GET /payments?orderId', user.auth),
  ]);
}
