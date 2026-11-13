import { createHash } from 'node:crypto';

// 규모마다 만들 개수. 구매자 수에 맞춰 자녀 · 찜 · 장바구니 · 주문을 회원마다 고르게 나눈다
export const SCALES = {
  small: { buyers: 10, sellers: 3, products: 20, favoritesPerBuyer: 4, cartLinesPerBuyer: 2, ordersPerBuyer: 3 },
  medium: { buyers: 1000, sellers: 50, products: 5000, favoritesPerBuyer: 30, cartLinesPerBuyer: 3, ordersPerBuyer: 12 },
};

// 주문이 멈출 단계. 순서가 진행 순서다(결제완료 → 발송 → 배송완료 → 구매확정)
export const STAGES = ['PAID', 'SHIPPED', 'DELIVERED', 'CONFIRMED'];
const STAGE_WEIGHTS = [['PAID', 8], ['SHIPPED', 8], ['DELIVERED', 9], ['CONFIRMED', 75]];
const REVIEW_RATE = 0.6;

// 구매자마다 자녀 수. 평균 2명이고 자녀가 없는 회원도 있다
const CHILD_COUNTS = [2, 1, 3, 0, 4, 2, 1, 3, 2, 2];

const SEED = 'kidly-seed-bulk-v1';

function hash32(text) {
  return createHash('sha256').update(text).digest().readUInt32LE(0);
}

// 같은 이름이면 같은 수열을 내는 난수(mulberry32). 항목마다 따로 만들어 개수가 바뀌어도 앞 항목의 값이 그대로다
export function rngFor(name) {
  let a = hash32(`${SEED}:${name}`);
  const next = () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    int: (min, max) => min + Math.floor(next() * (max - min + 1)),
    pick: (list) => list[Math.floor(next() * list.length)],
    chance: (p) => next() < p,
    // 앞 번호일수록 많이 뽑힌다(인기 상품)
    skewed: (n) => Math.min(n - 1, Math.floor(n * next() ** 2)),
    weighted: (pairs) => {
      const total = pairs.reduce((sum, [, w]) => sum + w, 0);
      let r = next() * total;
      for (const [value, w] of pairs) {
        r -= w;
        if (r < 0) return value;
      }
      return pairs.at(-1)[0];
    },
  };
}

// 주문 Idempotency-Key. 같은 규모 · 같은 번호면 늘 같은 UUID라 다시 돌려도 주문이 두 번 생기지 않는다
export function orderIdempotencyKey(scale, orderKey) {
  const h = createHash('sha256').update(`${SEED}:${scale}:${orderKey}`).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

const pad = (n, width) => String(n).padStart(width, '0');

const FAMILY_NAMES = ['김', '이', '박', '최', '정', '강', '조', '윤', '장', '임', '한', '오', '서', '신', '권'];
const GIVEN_FIRST = ['서', '하', '지', '도', '시', '예', '수', '유', '민', '주', '윤', '채', '은', '이', '아'];
const GIVEN_SECOND = ['윤', '준', '우', '아', '린', '원', '호', '은', '율', '안', '현', '진', '온', '나', '빈'];
const STORE_WORDS = ['꼬마', '새싹', '몽글', '토닥', '햇살', '도토리', '구름', '방울', '포근', '말랑'];
const STORE_SUFFIXES = ['상점', '옷장', '키즈', '마켓', '하우스'];
const ROADS = ['테헤란로', '올림픽로', '세종대로', '판교역로', '해운대로', '중앙대로', '대학로', '공원로'];
const CITIES = ['서울특별시 강남구', '서울특별시 송파구', '경기도 성남시 분당구', '부산광역시 해운대구', '대전광역시 유성구', '인천광역시 연수구'];
const REVIEW_OPENERS = ['아이가 정말 좋아해요.', '생각보다 훨씬 예뻐요.', '배송이 빨라서 좋았어요.', '가격 대비 만족스러워요.', '재구매 의사 있어요.', '선물용으로 샀는데 반응이 좋아요.'];
const REVIEW_DETAILS = ['소재가 부드러워서 피부에 자극이 없어요.', '세탁해도 늘어나지 않아요.', '색감이 사진이랑 거의 같아요.', '바느질이 꼼꼼해요.', '조금 얇아서 봄가을에 입히기 좋아요.', '활동하기 편하다고 해요.'];
const REVIEW_CLOSERS = ['다른 색도 사려고요.', '추천합니다.', '동생 것도 주문했어요.', '다음 시즌에도 살게요.', ''];

function koreanName(rng) {
  return rng.pick(FAMILY_NAMES) + rng.pick(GIVEN_FIRST) + rng.pick(GIVEN_SECOND);
}

export function buyerKey(i) {
  return `buyer-${pad(i + 1, 4)}`;
}

export function sellerKey(j) {
  return `seller-${pad(j + 1, 3)}`;
}

export function productKey(k) {
  return `product-${pad(k + 1, 5)}`;
}

export function orderKey(o) {
  return `order-${pad(o + 1, 6)}`;
}

export function buyerPlan(i) {
  const rng = rngFor(`buyer:${i}`);
  const n = pad(i + 1, 4);
  return {
    key: buyerKey(i),
    nickname: `시드구매자${n}`,
    childCount: CHILD_COUNTS[i % CHILD_COUNTS.length],
    address: {
      recipientName: koreanName(rng),
      recipientPhone: `010-0000-${n}`,
      zipCode: pad(rng.int(1000, 63000), 5),
      roadAddress: `${rng.pick(CITIES)} ${rng.pick(ROADS)} ${rng.int(1, 400)}`,
      detailAddress: `${rng.int(1, 30)}층 ${rng.int(1, 20)}호`,
      isDefault: true,
    },
  };
}

export function sellerPlan(j) {
  const rng = rngFor(`seller:${j}`);
  const n = pad(j + 1, 3);
  return {
    key: sellerKey(j),
    nickname: `시드판매자${n}`,
    application: {
      businessName: `${rng.pick(STORE_WORDS)}${rng.pick(STORE_SUFFIXES)} ${n}`,
      representativeName: koreanName(rng),
      businessRegistrationNumber: `9${pad(j + 1, 9)}`,
    },
  };
}

// 자녀 이름은 회원 안에서 겹치지 않아야 다시 돌릴 때 이름으로 찾을 수 있다
export function childPlans(i) {
  const rng = rngFor(`children:${i}`);
  const names = new Set();
  const children = [];
  for (let c = 0; c < CHILD_COUNTS[i % CHILD_COUNTS.length]; c++) {
    let name;
    do {
      name = rng.pick(GIVEN_FIRST) + rng.pick(GIVEN_SECOND);
    } while (names.has(name));
    names.add(name);
    // 기준일 2026-06-01에서 1~11살. 만 14세 미만 조건을 몇 해 동안 지킨다
    const ageMonths = rng.int(12, 11 * 12 + 11);
    const birth = new Date(Date.UTC(2026, 5 - ageMonths, rng.int(1, 28)));
    const age = ageMonths / 12;
    const heightCm = (75 + 6 * age + rng.int(-40, 40) / 10).toFixed(1);
    const weightKg = (9 + 2.4 * age + rng.int(-20, 20) / 10).toFixed(1);
    const optionalConsent = rng.chance(0.5);
    children.push({
      name,
      birthDate: birth.toISOString().slice(0, 10),
      gender: rng.pick(['MALE', 'FEMALE']),
      heightCm: Number(heightCm),
      weightKg: Number(weightKg),
      bodyType: optionalConsent ? rng.pick(['SLIM', 'NORMAL', 'CHUNKY']) : null,
      preferredFit: optionalConsent ? rng.pick(['SLIM', 'REGULAR', 'LOOSE']) : null,
      requiredConsent: true,
      optionalConsent,
    });
  }
  return children;
}

// 카테고리 이름별 상품명 낱말. 목록에 없는 카테고리는 기본 낱말을 쓴다
const PRODUCT_WORDS = {
  상의: ['맨투맨', '티셔츠', '후드티', '니트', '셔츠', '긴팔티'],
  하의: ['조거 팬츠', '청바지', '레깅스', '반바지', '와이드 팬츠', '트레이닝 팬츠'],
  아우터: ['점퍼', '바람막이', '패딩 조끼', '가디건', '코트', '플리스 자켓'],
  원피스: ['플레어 원피스', '셔츠 원피스', '니트 원피스', '점퍼스커트'],
  모자: ['버킷햇', '야구모자', '비니', '썬캡'],
  양말: ['니삭스', '발목양말', '수면양말', '논슬립 양말'],
};
const DEFAULT_WORDS = ['데일리 아이템', '베이직 아이템'];
const ADJECTIVES = ['데일리', '베이직', '코지', '소프트', '컬러', '스트라이프', '빈티지', '포근한', '산뜻한', '루즈핏'];
const MATERIALS = ['코튼', '린넨', '기모', '골지', '데님', '쭈리', '메쉬', '울'];
const BRANDS = ['리틀모먼트', '키들리베이직', '몽슈슈', '꼬까옷', '데일리키즈', '포레스트베베'];

// categories: 말단 카테고리(입력 항목 포함) 목록. 상품 하나의 등록 요청 재료를 정한다
export function productPlan(k, sellerCount, categories) {
  const rng = rngFor(`product:${k}`);
  const category = categories[k % categories.length];
  const words = PRODUCT_WORDS[category.name] ?? DEFAULT_WORDS;
  const name = `${rng.pick(ADJECTIVES)} ${rng.pick(MATERIALS)} ${rng.pick(words)} (KD-${pad(k + 1, 5)})`;
  return {
    key: productKey(k),
    sellerIndex: k % sellerCount,
    categoryId: category.categoryId,
    name,
    brand: rng.pick(BRANDS),
    description: `${name}. 아이 피부에 닿는 안쪽까지 부드럽게 마감했어요.`,
    price: rng.int(9, 69) * 1000 - 100,
    colorCount: rng.int(1, 2),
    sizeCount: rng.int(2, 3),
    seed: k,
  };
}

// 회원마다 찜할 상품 번호(겹치지 않음)
export function favoritePlan(i, scale) {
  const { products, favoritesPerBuyer } = SCALES[scale];
  const rng = rngFor(`favorites:${i}`);
  const picked = new Set();
  const want = Math.min(favoritesPerBuyer, products);
  while (picked.size < want) picked.add(rng.skewed(products));
  return [...picked];
}

// 회원마다 장바구니 줄: 상품 번호 · 옵션 자리 · 자녀 자리(없으면 null)
export function cartPlan(i, scale) {
  const { products, cartLinesPerBuyer } = SCALES[scale];
  const rng = rngFor(`cart:${i}`);
  const childCount = CHILD_COUNTS[i % CHILD_COUNTS.length];
  const seen = new Set();
  const lines = [];
  while (lines.length < Math.min(cartLinesPerBuyer, products)) {
    const productIndex = rng.skewed(products);
    if (seen.has(productIndex)) continue;
    seen.add(productIndex);
    lines.push({
      productIndex,
      optionSlot: rng.int(0, 5),
      childSlot: childCount > 0 && rng.chance(0.5) ? rng.int(0, childCount - 1) : null,
      quantity: 1,
    });
  }
  return lines;
}

// 주문 하나. 회원은 번호를 돌려 가며 고르게 나눈다
export function orderPlan(o, scale) {
  const { buyers, products } = SCALES[scale];
  const rng = rngFor(`order:${o}`);
  const buyerIndex = o % buyers;
  const childCount = CHILD_COUNTS[buyerIndex % CHILD_COUNTS.length];
  const itemCount = Math.min(products, rng.chance(0.7) ? 1 : 2);
  const seen = new Set();
  const items = [];
  while (items.length < itemCount) {
    const productIndex = rng.skewed(products);
    if (seen.has(productIndex)) continue;
    seen.add(productIndex);
    items.push({
      productIndex,
      optionSlot: rng.int(0, 5),
      childSlot: childCount > 0 && rng.chance(0.5) ? rng.int(0, childCount - 1) : null,
      quantity: rng.chance(0.9) ? 1 : 2,
    });
  }
  const target = rng.weighted(STAGE_WEIGHTS);
  return {
    key: orderKey(o),
    buyerIndex,
    items,
    target,
    reviews: items.map(() => target === 'CONFIRMED' && rng.chance(REVIEW_RATE)
      ? reviewPlan(rng)
      : null),
  };
}

function reviewPlan(rng) {
  const rating = rng.weighted([[5, 55], [4, 28], [3, 10], [2, 4], [1, 3]]);
  const content = [rng.pick(REVIEW_OPENERS), rng.pick(REVIEW_DETAILS), rng.pick(REVIEW_CLOSERS)]
    .filter(Boolean).join(' ');
  return { rating, content, fit: rng.weighted([['TRUE_TO_SIZE', 70], ['SMALL', 15], ['LARGE', 15]]) };
}

export function totals(scale) {
  const s = SCALES[scale];
  let children = 0;
  for (let i = 0; i < s.buyers; i++) children += CHILD_COUNTS[i % CHILD_COUNTS.length];
  return {
    buyers: s.buyers,
    sellers: s.sellers,
    children,
    products: s.products,
    favorites: s.buyers * Math.min(s.favoritesPerBuyer, s.products),
    cartLines: s.buyers * Math.min(s.cartLinesPerBuyer, s.products),
    orders: s.buyers * s.ordersPerBuyer,
  };
}
