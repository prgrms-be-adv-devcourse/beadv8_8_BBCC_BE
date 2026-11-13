// Gateway HTTP 클라이언트. 로그에는 메서드 · 경로 · 상태 · 오류 제목만 남긴다(토큰 · 요청 본문은 남기지 않는다)

const TOKEN_TTL_MS = 20 * 60 * 1000; // 토큰 유효 30분보다 일찍 새로 받는다
const RETRY_DELAYS_MS = [500, 1000, 2000, 4000, 8000, 8000];
// Kafka 복사본이 아직 없어 생기는 4xx를 기다리는 시간
const LAG_DELAYS_MS = [1000, 1000, 2000, 2000, 3000, 3000, 5000, 5000, 5000, 5000, 10000, 10000, 10000, 10000, 10000, 10000, 10000, 10000];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export class HttpError extends Error {
  constructor(method, path, status, problem) {
    const title = problem?.title ?? '';
    const detail = String(problem?.detail ?? '').slice(0, 200);
    super(`${method} ${path} → ${status} ${title}${detail ? `: ${detail}` : ''}`);
    this.status = status;
    this.title = title;
  }

  get retryable() {
    return this.status === 429 || this.status >= 500;
  }
}

function isRetryable(error) {
  return error instanceof HttpError ? error.retryable : true; // fetch가 던진 네트워크 오류
}

// 5xx · 429 · 네트워크 오류면 물러났다가 fn 전체를 다시 부른다. fn은 지금 상태를 읽고 모자란 것만 하므로 다시 불러도 같다
export async function converge(fn) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fn();
    } catch (error) {
      if (!isRetryable(error) || attempt >= RETRY_DELAYS_MS.length) throw error;
      await sleep(RETRY_DELAYS_MS[attempt]);
    }
  }
}

// 복사본 지연으로 나는 오류(isLag)면 기다렸다가 다시 부른다
export async function waitForReplica(fn, isLag) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fn();
    } catch (error) {
      if (!(error instanceof HttpError) || !isLag(error) || attempt >= LAG_DELAYS_MS.length) throw error;
      await sleep(LAG_DELAYS_MS[attempt]);
    }
  }
}

// 조건이 참이 될 때까지 다시 읽는다(결제 완료 등)
export async function poll(read, done, { intervalMs = 1000, timeoutMs = 120000, what }) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await read();
    if (done(value)) return value;
    if (Date.now() > deadline) throw new Error(`${what}: ${timeoutMs / 1000}초 안에 끝나지 않았다`);
    await sleep(intervalMs);
  }
}

export function createClient({ baseUrl, admin }) {
  const tokens = new Map();
  let adminToken = null;
  let requestCount = 0;

  async function send(method, path, { token, body, headers = {} } = {}) {
    requestCount++;
    const response = await fetch(baseUrl + path, {
      method,
      headers: {
        accept: 'application/json',
        ...(body === undefined ? {} : { 'content-type': 'application/json' }),
        ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });
    const text = await response.text();
    const json = text ? safeJson(text) : null;
    if (!response.ok) throw new HttpError(method, path, response.status, json);
    return { status: response.status, body: json };
  }

  // 읽기 · 멱등 요청은 바로 다시 보낸다
  const call = (method, path, options) => converge(() => send(method, path, options));

  async function memberToken(memberId, nickname, roles) {
    const cacheKey = `${memberId}:${roles.join(',')}`;
    const cached = tokens.get(cacheKey);
    if (cached && Date.now() - cached.at < TOKEN_TTL_MS) return cached.token;
    const { body } = await call('POST', '/api/v1/dev/tokens', { body: { memberId, nickname, roles } });
    tokens.set(cacheKey, { token: body.accessToken, at: Date.now() });
    return body.accessToken;
  }

  async function getAdminToken() {
    if (adminToken && Date.now() - adminToken.at < TOKEN_TTL_MS) return adminToken.token;
    const { body } = await call('POST', '/api/v1/auth/admin/login', { body: admin });
    adminToken = { token: body.accessToken, at: Date.now() };
    return adminToken.token;
  }

  return { send, call, memberToken, adminToken: getAdminToken, requests: () => requestCount };
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
