import { HttpError, converge, poll, waitForReplica } from './api.mjs';
import {
  SCALES, STAGES, buyerKey, buyerPlan, cartPlan, childPlans, favoritePlan, orderIdempotencyKey, orderPlan,
  productKey, productPlan, rngFor, sellerKey, sellerPlan,
} from './plan.mjs';

const BUYER = ['BUYER'];
const SELLER = ['BUYER', 'SELLER'];
const INITIAL_QUANTITY = 999;
const DEV_CHARGE_MAX = 1_000_000;

const isNotFound = (e) => e.status === 404;
const isConflict = (e) => e.status === 409;

// 차례대로 돈다. 앞 단계가 만든 ID를 뒤 단계가 manifest에서 읽는다
export const PHASES = [
  ['members', seedMembers],
  ['sellers', seedSellers],
  ['addresses', seedAddresses],
  ['children', seedChildren],
  ['products', seedProducts],
  ['wallets', seedWallets],
  ['favorites', seedFavorites],
  ['orders', seedOrders],
  ['fulfillment', seedFulfillment],
  ['reviews', seedReviews],
  // 결제된 주문과 같은 (옵션, 자녀) 칸은 주문 도메인이 장바구니에서 빼므로(#641) 주문 뒤에 담는다
  ['carts', seedCarts],
];

// 작업을 concurrency개씩 돌린다. 하나라도 실패하면 새 작업을 꺼내지 않고, 돌던 작업이 끝난 뒤 그 오류를 던진다
async function runPool(items, concurrency, task) {
  const counts = { created: 0, skipped: 0 };
  let next = 0;
  let failure = null;
  async function worker() {
    while (failure === null && next < items.length) {
      const item = items[next++];
      try {
        counts[(await task(item)) ? 'created' : 'skipped']++;
      } catch (error) {
        failure ??= error;
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  if (failure) throw failure;
  return { total: items.length, ...counts };
}

const range = (n) => Array.from({ length: n }, (_, i) => i);

function memberIdOf(ctx, key) {
  const id = ctx.manifest.members[key];
  if (id === undefined) throw new Error(`${key}의 회원 ID가 기록에 없다. members 단계를 먼저 돌린다`);
  return id;
}

function buyerToken(ctx, i) {
  return ctx.api.memberToken(memberIdOf(ctx, buyerKey(i)), buyerPlan(i).nickname, BUYER);
}

function sellerToken(ctx, j) {
  return ctx.api.memberToken(memberIdOf(ctx, sellerKey(j)), sellerPlan(j).nickname, SELLER);
}

async function seedMembers(ctx) {
  const { buyers, sellers } = SCALES[ctx.scale];
  const plans = [...range(buyers).map(buyerPlan), ...range(sellers).map(sellerPlan)];
  return runPool(plans, ctx.concurrency, async ({ key, nickname }) => {
    if (ctx.manifest.members[key] !== undefined) return false;
    const { body } = await ctx.api.call('POST', '/api/v1/dev/members', { body: { key, nickname } });
    ctx.manifest.members[key] = body.memberId;
    ctx.touch();
    return body.created;
  });
}

// 최근 신청을 읽어 없으면 신청하고, 심사 중이면 관리자가 승인한다
async function seedSellers(ctx) {
  return runPool(range(SCALES[ctx.scale].sellers), ctx.concurrency, async (j) => {
    const plan = sellerPlan(j);
    if (ctx.manifest.sellerApplications[plan.key] !== undefined) return false;
    const token = await ctx.api.memberToken(memberIdOf(ctx, plan.key), plan.nickname, BUYER);
    const path = '/api/v1/members/me/seller-application';
    let application = await converge(async () => {
      try {
        return (await ctx.api.send('GET', path, { token })).body;
      } catch (error) {
        if (!(error instanceof HttpError) || !isNotFound(error)) throw error;
        return (await ctx.api.send('POST', path, { token, body: plan.application })).body;
      }
    });
    if (application.status === 'PENDING') {
      const adminToken = await ctx.api.adminToken();
      application = (await ctx.api.call('POST', `/api/v1/admin/seller-applications/${application.id}/approve`,
        { token: adminToken })).body;
    }
    if (application.status !== 'APPROVED') {
      throw new Error(`${plan.key}의 판매자 신청이 ${application.status}라 승인할 수 없다`);
    }
    ctx.manifest.sellerApplications[plan.key] = Number(application.id);
    ctx.touch();
    return true;
  });
}

async function seedAddresses(ctx) {
  return runPool(range(SCALES[ctx.scale].buyers), ctx.concurrency, async (i) => {
    const plan = buyerPlan(i);
    if (ctx.manifest.addresses[plan.key] !== undefined) return false;
    const token = await buyerToken(ctx, i);
    const path = '/api/v1/members/me/addresses';
    const id = await converge(async () => {
      const existing = (await ctx.api.send('GET', path, { token })).body;
      if (existing.length > 0) return existing[0].id;
      return (await ctx.api.send('POST', path, { token, body: plan.address })).body.id;
    });
    ctx.manifest.addresses[plan.key] = Number(id);
    ctx.touch();
    return true;
  });
}

// 본인인증 기록을 만들고, 이름으로 찾아 없는 자녀만 등록한다
async function seedChildren(ctx) {
  return runPool(range(SCALES[ctx.scale].buyers), ctx.concurrency, async (i) => {
    const key = buyerKey(i);
    const plans = childPlans(i);
    if (plans.length === 0 || ctx.manifest.children[key]?.length === plans.length) return false;
    const token = await buyerToken(ctx, i);
    await ctx.api.call('POST', '/api/v1/dev/guardian-verifications', { token });
    ctx.manifest.guardians[key] = true;
    const ids = await converge(async () => {
      const existing = (await ctx.api.send('GET', '/api/v1/children', { token })).body;
      const result = [];
      for (const child of plans) {
        const found = existing.find((c) => c.name === child.name);
        result.push(Number(found ? found.id : (await ctx.api.send('POST', '/api/v1/children', { token, body: child })).body.id));
      }
      return result;
    });
    ctx.manifest.children[key] = ids;
    ctx.touch();
    return true;
  });
}

// 말단 카테고리와 입력 항목. 카테고리 ID는 환경마다 다르므로 API로 읽는다
async function loadCategories(ctx) {
  const tree = (await ctx.api.call('GET', '/api/v1/categories')).body;
  const leaves = [];
  const walk = (nodes) => nodes.forEach((n) => (n.leaf ? leaves.push(n) : walk(n.children)));
  walk(tree);
  const categories = [];
  for (const leaf of leaves) {
    const inputs = (await ctx.api.call('GET', `/api/v1/categories/${leaf.categoryId}/attributes`)).body;
    const sizeAttribute = inputs.attributes.find((a) => a.role === 'OPTION' && a.sizeRole);
    const sizes = sizeAttribute?.values.filter((v) => v.value !== 'FREE') ?? [];
    if (sizeAttribute && sizes.length < 3) continue; // 사이즈를 2~3개 고를 수 없는 카테고리는 쓰지 않는다
    categories.push({ categoryId: leaf.categoryId, name: leaf.name, ...inputs });
  }
  if (categories.length === 0) throw new Error('상품을 등록할 말단 카테고리가 없다');
  return categories;
}

// 권장 키(cm): 숫자 사이즈(110 등)는 그 값 앞뒤, 그 밖(44cm · 100-120mm 등)은 사이즈 순서로 정한다
function heightRange(sizeValue, position) {
  const n = Number(sizeValue.value);
  if (Number.isInteger(n) && n >= 50 && n <= 200) return [n - 5, n + 4];
  return [80 + position * 10, 89 + position * 10];
}

function toRegisterRequest(plan, category) {
  const rng = rngFor(`product-options:${plan.seed}`);
  const optionAttributes = category.attributes.filter((a) => a.role === 'OPTION');
  const sizeAttribute = optionAttributes.find((a) => a.sizeRole);
  const colorAttribute = optionAttributes.find((a) => !a.sizeRole);
  const sizeValues = sizeAttribute ? sizeAttribute.values.filter((v) => v.value !== 'FREE') : [];
  const sizeStart = rng.int(0, Math.max(0, sizeValues.length - plan.sizeCount));
  const sizes = sizeAttribute ? sizeValues.slice(sizeStart, sizeStart + plan.sizeCount) : [null];
  const colorStart = colorAttribute ? rng.int(0, colorAttribute.values.length - plan.colorCount) : 0;
  const colors = colorAttribute ? colorAttribute.values.slice(colorStart, colorStart + plan.colorCount) : [null];
  // 색상 · 사이즈 밖의 옵션 항목은 첫 값 하나로 고정한다
  const fixed = optionAttributes.filter((a) => a !== sizeAttribute && a !== colorAttribute).map((a) => a.values[0]);

  const options = [];
  for (const color of colors) {
    for (const size of sizes) {
      const valueIds = [color, size, ...fixed].filter(Boolean).map((v) => v.valueId);
      options.push({ valueIds, initialQuantity: INITIAL_QUANTITY, skuCode: `SEED-${plan.key}-${options.length + 1}` });
    }
  }
  const priceIds = new Set(optionAttributes.filter((a) => a.priceAffecting).flatMap((a) => a.values.map((v) => v.valueId)));
  const priceGroups = new Map(options.map((o) => {
    const ids = o.valueIds.filter((id) => priceIds.has(id));
    return [ids.join(','), ids];
  }));
  const prices = [...priceGroups.values()].map((valueIds) => ({ valueIds, price: plan.price }));

  const attributes = [];
  for (const attribute of category.attributes.filter((a) => a.role === 'INFO')) {
    if (attribute.inputType === 'TEXT') {
      attributes.push({ attributeId: attribute.attributeId, valueIds: [], text: '면 95%, 스판덱스 5%' });
    } else if (attribute.required) {
      attributes.push({ attributeId: attribute.attributeId, valueIds: [rng.pick(attribute.values).valueId], text: null });
    }
  }

  const sizeSpecs = sizes.filter(Boolean).map((size) => {
    const position = sizeValues.indexOf(size);
    const [minHeightCm, maxHeightCm] = heightRange(size, position);
    const measurements = Object.fromEntries(category.measurementItems.map((item, index) =>
      [item.code, 15 + index * 6 + position * 2]));
    return { sizeValueId: size.valueId, minHeightCm, maxHeightCm, measurements };
  });

  return {
    name: plan.name, brand: plan.brand, categoryId: plan.categoryId, description: plan.description,
    thumbnailUrl: null, imageUrls: [], attributes, options, prices, sizeSpecs,
  };
}

// 판매자의 상품 목록을 이름으로 찾는다. 등록 응답을 기록하기 전에 멈춘 실행의 상품을 다시 만들지 않으려고 쓴다
async function listSellerProducts(ctx, token) {
  const byName = new Map();
  for (let page = 0; ; page++) {
    const { body } = await ctx.api.send('GET', `/api/v1/seller/products?page=${page}&size=100`, { token });
    body.content.forEach((p) => byName.set(p.name, p));
    if (!body.hasNext) return byName;
  }
}

async function seedProducts(ctx) {
  const { products, sellers } = SCALES[ctx.scale];
  if (range(products).every((k) => ctx.manifest.products[productKey(k)]?.onSale)) {
    return { total: products, created: 0, skipped: products };
  }
  const categories = await loadCategories(ctx);
  const sellerListings = new Map();
  return runPool(range(products), ctx.concurrency, async (k) => {
    const plan = productPlan(k, sellers, categories);
    const recorded = ctx.manifest.products[plan.key];
    if (recorded?.onSale) return false;
    const token = await sellerToken(ctx, plan.sellerIndex);
    const product = await converge(async () => {
      if (recorded) return recorded;
      if (!sellerListings.has(plan.sellerIndex)) sellerListings.set(plan.sellerIndex, listSellerProducts(ctx, token));
      const found = (await sellerListings.get(plan.sellerIndex)).get(plan.name);
      if (found) {
        sellerListings.delete(plan.sellerIndex); // 다시 시도하면 목록을 새로 읽는다
        const detail = (await ctx.api.send('GET', `/api/v1/seller/products/${found.productId}`, { token })).body;
        return { productId: Number(found.productId), options: detail.options };
      }
      sellerListings.delete(plan.sellerIndex);
      const category = categories.find((c) => c.categoryId === plan.categoryId);
      const { body } = await ctx.api.send('POST', '/api/v1/seller/products', { token, body: toRegisterRequest(plan, category) });
      return { productId: Number(body.productId), options: body.options };
    });
    const entry = {
      productId: product.productId,
      sellerKey: sellerKey(plan.sellerIndex),
      options: product.options.map((o) => ({ optionId: Number(o.optionId), price: Number(o.price) })),
      onSale: false,
    };
    ctx.manifest.products[plan.key] = entry;
    ctx.touch();
    await ctx.api.call('PUT', `/api/v1/seller/products/${entry.productId}/sale-status`, { token, body: { status: 'ON_SALE' } });
    entry.onSale = true;
    ctx.touch();
    return true;
  });
}

function productEntry(ctx, productIndex) {
  const entry = ctx.manifest.products[productKey(productIndex)];
  if (!entry?.onSale) throw new Error(`${productKey(productIndex)}가 판매 중으로 기록되지 않았다. products 단계를 먼저 돌린다`);
  return entry;
}

function optionOf(ctx, line) {
  const { options } = productEntry(ctx, line.productIndex);
  return options[line.optionSlot % options.length];
}

function childIdOf(ctx, buyerIndex, childSlot) {
  if (childSlot === null) return null;
  const ids = ctx.manifest.children[buyerKey(buyerIndex)];
  if (!ids) throw new Error(`${buyerKey(buyerIndex)}의 자녀가 기록에 없다. children 단계를 먼저 돌린다`);
  return ids[childSlot];
}

function buyerOrders(ctx, i) {
  const { buyers, ordersPerBuyer } = SCALES[ctx.scale];
  return range(ordersPerBuyer).map((n) => orderPlan(i + n * buyers, ctx.scale));
}

// 아직 넣지 않은 주문의 금액만큼 잔액이 모자라면 그 차이만 충전한다. 잔액을 읽고 정하므로 다시 돌려도 더 넣지 않는다
async function seedWallets(ctx) {
  return runPool(range(SCALES[ctx.scale].buyers), ctx.concurrency, async (i) => {
    const key = buyerKey(i);
    const needed = buyerOrders(ctx, i)
      .filter((order) => ctx.manifest.orders[order.key] === undefined)
      .reduce((sum, order) => sum + order.items.reduce((s, line) => s + optionOf(ctx, line).price * line.quantity, 0), 0);
    if (needed === 0) return false;
    const token = await buyerToken(ctx, i);
    let charged = false;
    // 충전 한 번마다 잔액을 다시 읽는다. 충전 요청이 실패해도 다시 읽은 잔액으로 모자란 만큼만 넣는다
    for (;;) {
      const { balance } = (await ctx.api.call('GET', '/api/v1/wallets/me', { token })).body;
      if (balance >= needed) return charged;
      const amount = Math.min(needed - balance, DEV_CHARGE_MAX);
      await converge(() => ctx.api.send('POST', '/api/v1/dev/wallets/me/charges', { token, body: { amount } }));
      ctx.manifest.wallets[key] = (ctx.manifest.wallets[key] ?? 0) + amount;
      ctx.touch();
      charged = true;
    }
  });
}

async function seedFavorites(ctx) {
  return runPool(range(SCALES[ctx.scale].buyers), ctx.concurrency, async (i) => {
    const key = buyerKey(i);
    const done = new Set(ctx.manifest.favorites[key] ?? []);
    const wanted = favoritePlan(i, ctx.scale).map((p) => productEntry(ctx, p).productId).filter((id) => !done.has(id));
    if (wanted.length === 0) return false;
    const token = await buyerToken(ctx, i);
    for (const productId of wanted) {
      await waitForReplica(() => ctx.api.call('PUT', `/api/v1/favorites/${productId}`, { token }), isNotFound);
      done.add(productId);
      ctx.manifest.favorites[key] = [...done];
      ctx.touch();
    }
    return true;
  });
}

async function seedCarts(ctx) {
  return runPool(range(SCALES[ctx.scale].buyers), ctx.concurrency, async (i) => {
    const key = buyerKey(i);
    const lines = cartPlan(i, ctx.scale);
    if (ctx.manifest.cartItems[key]?.length === lines.length) return false;
    const token = await buyerToken(ctx, i);
    const ids = [];
    for (const line of lines) {
      const optionId = optionOf(ctx, line).optionId;
      const childId = childIdOf(ctx, i, line.childSlot);
      const id = await converge(async () => {
        const cart = (await ctx.api.send('GET', '/api/v1/cart', { token })).body;
        const found = cart.items.find((c) => Number(c.optionId) === optionId && (c.childId === null ? null : Number(c.childId)) === childId);
        if (found) return found.cartItemId;
        const body = { optionId, childId, quantity: line.quantity };
        return (await waitForReplica(() => ctx.api.send('POST', '/api/v1/cart/items', { token, body }), isNotFound)).body.cartItemId;
      });
      ids.push(Number(id));
    }
    ctx.manifest.cartItems[key] = ids;
    ctx.touch();
    return true;
  });
}

function ordersInPlan(ctx) {
  const { buyers, ordersPerBuyer } = SCALES[ctx.scale];
  return range(buyers * ordersPerBuyer).map((o) => orderPlan(o, ctx.scale));
}

// Idempotency-Key가 같으면 서버가 같은 주문을 돌려주므로, 기록 전에 멈췄어도 다시 보내면 된다
async function seedOrders(ctx) {
  return runPool(ordersInPlan(ctx), ctx.concurrency, async (order) => {
    if (ctx.manifest.orders[order.key]?.stage) return false;
    const i = order.buyerIndex;
    const token = await buyerToken(ctx, i);
    const address = buyerPlan(i).address;
    const body = {
      items: order.items.map((line) => {
        const option = optionOf(ctx, line);
        return { optionId: option.optionId, childId: childIdOf(ctx, i, line.childSlot), quantity: line.quantity, unitPrice: option.price };
      }),
      shippingAddress: {
        recipientName: address.recipientName,
        phone: address.recipientPhone,
        zipCode: address.zipCode,
        address: `${address.roadAddress} ${address.detailAddress}`,
      },
    };
    const headers = { 'Idempotency-Key': orderIdempotencyKey(ctx.scale, order.key) };
    const placed = (await waitForReplica(
      () => ctx.api.call('POST', '/api/v1/orders', { token, body, headers }), isNotFound)).body;
    ctx.manifest.orders[order.key] = { orderId: Number(placed.orderId), buyerKey: buyerKey(i) };
    ctx.touch();
    const detail = await poll(
      async () => (await ctx.api.call('GET', `/api/v1/orders/${placed.orderId}`, { token })).body,
      (d) => d.status === 'CANCELLED' || (d.status === 'PAID' && d.deliveries.length > 0),
      { what: `${order.key} 결제` });
    if (detail.status === 'CANCELLED') throw new Error(`${order.key}가 결제되지 않고 취소됐다(${detail.cancelReason})`);
    ctx.manifest.orders[order.key] = {
      orderId: Number(detail.orderId),
      buyerKey: buyerKey(i),
      items: order.items.map((line, n) => {
        const item = detail.items.find((it) => Number(it.optionId) === body.items[n].optionId);
        return { orderItemId: Number(item.orderItemId), size: item.size };
      }),
      sellerOrders: detail.deliveries.map((d) => ({ sellerOrderId: Number(d.sellerOrderId), sellerId: Number(d.sellerId) })),
      stage: 'PAID',
    };
    ctx.touch();
    return true;
  });
}

// 단계마다 다음 단계로 넘기는 요청. 모두 서버가 이미 넘어간 상태면 그대로 200을 주거나, 상태를 먼저 읽고 건너뛴다
const ADVANCE = {
  SHIPPED: async (ctx, order, entry) => {
    const token = await buyerToken(ctx, order.buyerIndex);
    const detail = (await ctx.api.call('GET', `/api/v1/orders/${entry.orderId}`, { token })).body;
    for (const delivery of detail.deliveries.filter((d) => d.status === 'READY')) {
      const sellerToken = await sellerTokenById(ctx, Number(delivery.sellerId));
      await ctx.api.call('POST', `/api/v1/seller/orders/${delivery.sellerOrderId}/ship`,
        { token: sellerToken, body: { carrier: 'DUMMY', trackingNumber: `SEED${delivery.sellerOrderId}` } });
    }
  },
  DELIVERED: async (ctx, order, entry) => {
    for (const { sellerOrderId, sellerId } of entry.sellerOrders) {
      const token = await sellerTokenById(ctx, sellerId);
      await ctx.api.call('POST', `/api/v1/seller/orders/${sellerOrderId}/deliver`, { token });
    }
  },
  CONFIRMED: async (ctx, order, entry) => {
    const token = await buyerToken(ctx, order.buyerIndex);
    for (const { orderItemId } of entry.items) {
      await ctx.api.call('POST', `/api/v1/orders/${entry.orderId}/items/${orderItemId}/confirm`, { token });
    }
  },
};

function sellerTokenById(ctx, memberId) {
  const j = range(SCALES[ctx.scale].sellers).find((n) => ctx.manifest.members[sellerKey(n)] === memberId);
  if (j === undefined) throw new Error(`판매자 회원 ${memberId}가 시드 판매자가 아니다`);
  return sellerToken(ctx, j);
}

async function seedFulfillment(ctx) {
  return runPool(ordersInPlan(ctx), ctx.concurrency, async (order) => {
    const entry = ctx.manifest.orders[order.key];
    if (!entry?.stage) throw new Error(`${order.key}가 결제완료로 기록되지 않았다. orders 단계를 먼저 돌린다`);
    const from = STAGES.indexOf(entry.stage);
    const to = STAGES.indexOf(order.target);
    if (from >= to) return false;
    for (const stage of STAGES.slice(from + 1, to + 1)) {
      await ADVANCE[stage](ctx, order, entry);
      entry.stage = stage;
      ctx.touch();
    }
    return true;
  });
}

// 구매확정 뒤 작성 자격이 Kafka로 커뮤니티에 닿을 때까지 403이 날 수 있어 기다린다. 409는 이미 쓴 리뷰다
async function seedReviews(ctx) {
  const tasks = [];
  for (const order of ordersInPlan(ctx)) {
    order.reviews.forEach((review, n) => review && tasks.push({ order, review, n }));
  }
  return runPool(tasks, ctx.concurrency, async ({ order, review, n }) => {
    const entry = ctx.manifest.orders[order.key];
    if (entry?.stage !== 'CONFIRMED') throw new Error(`${order.key}가 구매확정으로 기록되지 않았다. fulfillment 단계를 먼저 돌린다`);
    const item = entry.items[n];
    if (ctx.manifest.reviews[item.orderItemId] !== undefined) return false;
    const token = await buyerToken(ctx, order.buyerIndex);
    const body = {
      orderItemId: String(item.orderItemId),
      rating: review.rating,
      content: review.content,
      ...(item.size ? { fit: review.fit } : {}),
    };
    let reviewId;
    try {
      reviewId = (await waitForReplica(
        () => ctx.api.call('POST', '/api/v1/reviews', { token, body }), (e) => e.status === 403)).body.reviewId;
    } catch (error) {
      if (!(error instanceof HttpError) || !isConflict(error)) throw error;
      reviewId = await findMyReviewId(ctx, token, item.orderItemId);
    }
    ctx.manifest.reviews[item.orderItemId] = Number(reviewId);
    ctx.touch();
    return true;
  });
}

async function findMyReviewId(ctx, token, orderItemId) {
  let cursor = null;
  do {
    const query = `size=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const { body } = await ctx.api.call('GET', `/api/v1/reviews/me?${query}`, { token });
    const found = body.items.find((r) => Number(r.orderItemId) === orderItemId);
    if (found) return found.reviewId;
    cursor = body.nextCursor;
  } while (cursor);
  throw new Error(`주문상품 ${orderItemId}의 리뷰가 이미 있다는데 내 리뷰에서 찾지 못했다`);
}
