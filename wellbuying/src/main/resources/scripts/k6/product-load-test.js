// k6 부하 테스트 - 상품 조회/검색(D+E트랙) 기능 전체
//
// 대상 API (모두 임승빈 담당, 실제 구현 기준):
//   - GET /api/categories          카테고리 트리 조회 (@Cacheable("categoryTree"))
//   - GET /api/products             목록 조회 (커서 페이지네이션, LATEST/POPULAR/PRICE_ASC/PRICE_DESC)
//   - GET /api/products/{id}        상세 조회
//   - GET /api/products/search      키워드 검색 (OpenSearch + Nori, cursor=search_after)
//
// 실행 예시
//   전체 시나리오 실행:            k6 run product-load-test.js
//   가벼운 스모크 테스트:          k6 run -e QUICK=true product-load-test.js
//   특정 시나리오만 실행:          k6 run -e ONLY=search,detail product-load-test.js
//   대상 서버 지정:                k6 run -e BASE_URL=http://localhost:8080 product-load-test.js
//
// ONLY로 고를 수 있는 이름: categories, list_latest, list_filter, list_popular, list_price, detail, search

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PAGES_PER_ITER = 5;
const DETAIL_FANOUT = 3; // 목록 한 페이지에서 상세로 이어서 클릭해보는 개수

const ADJS = ['유기농', '프리미엄', '베스트', '신선한', '천연', '국내산', '수입', '고품질', '특가', '한정'];
const NOUNS = ['비타민', '마스크', '텀블러', '에코백', '쿠션', '영양제', '셔츠', '신발', '가방', '모자'];
const KEYWORDS = [...NOUNS, ...ADJS, ...ADJS.flatMap((a) => NOUNS.map((n) => `${a} ${n}`))];

const MIN_PRICE_POOL = [null, 0, 5000, 10000, 20000];
const MAX_PRICE_POOL = [null, 20000, 50000, 100000];

const STAGES = __ENV.QUICK
  ? [{ duration: '15s', target: 5 }]
  : [
      { duration: '30s', target: 10 },
      { duration: '1m', target: 50 },
      { duration: '30s', target: 100 },
      { duration: '1m', target: 100 },
      { duration: '30s', target: 0 },
    ];

// -e ONLY=list_latest,search 처럼 넘기면 해당 시나리오만 골라서 실행 (기본값: 전체 실행)
const ONLY = (__ENV.ONLY || '').split(',').map((s) => s.trim()).filter(Boolean);
function wants(name) {
  return ONLY.length === 0 || ONLY.includes(name);
}

const ALL_SCENARIOS = {
  categories: { executor: 'ramping-vus', exec: 'categoriesTest', stages: STAGES, tags: { test_type: 'categories' } },
  list_latest: { executor: 'ramping-vus', exec: 'listLatestTest', stages: STAGES, tags: { test_type: 'list_latest' } },
  list_filter: { executor: 'ramping-vus', exec: 'listFilterTest', stages: STAGES, tags: { test_type: 'list_filter' } },
  list_popular: { executor: 'ramping-vus', exec: 'listPopularTest', stages: STAGES, tags: { test_type: 'list_popular' } },
  list_price: { executor: 'ramping-vus', exec: 'listPriceTest', stages: STAGES, tags: { test_type: 'list_price' } },
  detail: { executor: 'ramping-vus', exec: 'detailTest', stages: STAGES, tags: { test_type: 'detail' } },
  search: { executor: 'ramping-vus', exec: 'searchTest', stages: STAGES, tags: { test_type: 'search' } },
};

const scenarios = {};
for (const [name, cfg] of Object.entries(ALL_SCENARIOS)) {
  if (wants(name)) scenarios[name] = cfg;
}

export const options = {
  scenarios,
  thresholds: {
    'http_req_duration{test_type:categories}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{test_type:list_latest}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:list_filter}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:list_popular}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:list_price}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:detail}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{test_type:search}': ['p(95)<3000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildQuery(params) {
  const parts = [];
  for (const key of Object.keys(params)) {
    const value = params[key];
    if (value !== null && value !== undefined && value !== '') {
      parts.push(`${key}=${encodeURIComponent(value)}`);
    }
  }
  return parts.join('&');
}

// 커서를 따라 최대 PAGES_PER_ITER 페이지까지 넘기면서 조회하고, 만난 상품 id 목록을 반환
function walkCursorPages(buildUrl, tagLabel) {
  let cursor = null;
  const ids = [];
  for (let i = 0; i < PAGES_PER_ITER; i++) {
    const res = http.get(buildUrl(cursor));

    const statusOk = check(res, { [`${tagLabel} status 200`]: (r) => r.status === 200 });
    if (!statusOk) break;

    const body = res.json();
    const hasContent = Array.isArray(body.content) && body.content.length > 0;
    check(res, { [`${tagLabel} has content`]: () => hasContent });
    if (!hasContent) break;

    for (const item of body.content) {
      if (item && item.id) ids.push(item.id);
    }

    if (!body.hasNext || !body.nextCursor) break;
    cursor = body.nextCursor;
    sleep(0.3);
  }
  return ids;
}

// setup(): 실제 카테고리 트리를 한 번 조회해서 리프 카테고리 id를 뽑아둔다.
// 카테고리 id를 하드코딩하지 않기 때문에 시드 데이터가 바뀌어도 그대로 재사용 가능.
export function setup() {
  const res = http.get(`${BASE_URL}/api/categories`);
  check(res, { 'setup: 카테고리 조회 200': (r) => r.status === 200 });

  const leafIds = [];
  function walk(nodes) {
    for (const n of nodes) {
      if (!n.children || n.children.length === 0) {
        leafIds.push(n.id);
      } else {
        walk(n.children);
      }
    }
  }
  try {
    walk(res.json());
  } catch (e) {
    // 카테고리 조회 실패 시에도 나머지 시나리오는 계속 돌 수 있게 빈 배열로 처리
  }
  return { categoryIds: leafIds.length > 0 ? leafIds : [null] };
}

// 1) 카테고리 트리 조회 - 캐시 적중 시 응답 속도 확인용
export function categoriesTest() {
  const res = http.get(`${BASE_URL}/api/categories`);
  check(res, {
    'categories status 200': (r) => r.status === 200,
    'categories has data': (r) => {
      try {
        return Array.isArray(r.json()) && r.json().length > 0;
      } catch (e) {
        return false;
      }
    },
  });
  sleep(1);
}

// 2) 목록 조회 - 필터 없는 기본(LATEST) 정렬
export function listLatestTest() {
  walkCursorPages((cursor) => {
    const qs = buildQuery({ size: 20, cursor });
    return `${BASE_URL}/api/products?${qs}`;
  }, 'list_latest');
  sleep(1);
}

// 3) 목록 조회 - 카테고리 + 가격 필터 (복합 인덱스/동적 쿼리 경로 확인용)
export function listFilterTest(data) {
  const categoryId = pick(data.categoryIds);
  const minPrice = pick(MIN_PRICE_POOL);
  const maxPrice = pick(MAX_PRICE_POOL);

  walkCursorPages((cursor) => {
    const qs = buildQuery({
      size: 20,
      category: categoryId,
      minPrice,
      maxPrice,
      cursor,
    });
    return `${BASE_URL}/api/products?${qs}`;
  }, 'list_filter');
  sleep(1);
}

// 4) 목록 조회 - 인기순 (POPULAR, view_count 기반 커서)
export function listPopularTest() {
  walkCursorPages((cursor) => {
    const qs = buildQuery({ size: 20, sort: 'POPULAR', cursor });
    return `${BASE_URL}/api/products?${qs}`;
  }, 'list_popular');
  sleep(1);
}

// 5) 목록 조회 - 가격순 (PRICE_ASC/PRICE_DESC, price 기반 커서)
export function listPriceTest() {
  const sort = pick(['PRICE_ASC', 'PRICE_DESC']);
  walkCursorPages((cursor) => {
    const qs = buildQuery({ size: 20, sort, cursor });
    return `${BASE_URL}/api/products?${qs}`;
  }, 'list_price');
  sleep(1);
}

// 6) 상세 조회 - "목록 보다가 몇 개 클릭해서 들어가본다"는 실사용 흐름 재현
//    (랜덤 id를 지어내지 않고 방금 목록에서 받은 실제 id만 사용 -> 존재하지 않는 id로 인한 노이즈 없음)
export function detailTest() {
  const ids = walkCursorPages((cursor) => {
    const qs = buildQuery({ size: 20, cursor });
    return `${BASE_URL}/api/products?${qs}`;
  }, 'detail_source_list');

  const sample = ids.sort(() => 0.5 - Math.random()).slice(0, DETAIL_FANOUT);
  for (const id of sample) {
    const res = http.get(`${BASE_URL}/api/products/${id}`);
    check(res, { 'detail status 200': (r) => r.status === 200 });
    sleep(0.2);
  }
  sleep(1);
}

// 7) 키워드 검색 (OpenSearch + Nori, RELEVANCE 정렬 + search_after 커서)
export function searchTest() {
  const kw = pick(KEYWORDS);
  walkCursorPages((cursor) => {
    const qs = buildQuery({ keyword: kw, size: 20, cursor });
    return `${BASE_URL}/api/products/search?${qs}`;
  }, 'search');
  sleep(1);
}

export default function () {
  listLatestTest();
}
