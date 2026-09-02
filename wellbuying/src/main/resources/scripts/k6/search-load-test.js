import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PAGES_PER_ITER = 5;

const ADJS  = ['유기농', '프리미엄', '베스트', '신선한', '천연', '국내산', '수입', '고품질', '특가', '한정'];
const NOUNS = ['비타민', '마스크', '텀블러', '에코백', '쿠션', '영양제', '셔츠', '신발', '가방', '모자'];
const KEYWORDS = [...NOUNS, ...ADJS, ...ADJS.flatMap(a => NOUNS.map(n => `${a} ${n}`))];

const STAGES = __ENV.QUICK
  ? [{ duration: '15s', target: 5 }]
  : [
      { duration: '30s', target: 10  },
      { duration: '1m',  target: 50  },
      { duration: '30s', target: 100 },
      { duration: '1m',  target: 100 },
      { duration: '30s', target: 0   },
    ];

export const options = {
  scenarios: {
    search_api: { executor: 'ramping-vus', exec: 'searchTest', stages: STAGES, tags: { test_type: 'search' } },
    list_api:   { executor: 'ramping-vus', exec: 'listTest',   stages: STAGES, tags: { test_type: 'list'   } },
  },
  thresholds: {
    'http_req_duration{test_type:search}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:list}':   ['p(95)<3000', 'p(99)<5000'],
    'http_req_failed': ['rate<0.05'],
  },
};

export function searchTest() {
  const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  let cursor = null;
  for (let i = 0; i < PAGES_PER_ITER; i++) {
    const url = cursor
      ? `${BASE_URL}/api/products/search?keyword=${encodeURIComponent(kw)}&size=20&cursor=${encodeURIComponent(cursor)}`
      : `${BASE_URL}/api/products/search?keyword=${encodeURIComponent(kw)}&size=20`;
    const res = http.get(url);

    const statusOk = check(res, { 'search status 200': (r) => r.status === 200 });
    if (!statusOk) break;

    const body = res.json();
    const hasContent = Array.isArray(body.content) && body.content.length > 0;
    check(res, { 'search has content': () => hasContent });
    if (!hasContent) break;

    if (!body.hasNext || !body.nextCursor) break;
    cursor = body.nextCursor;
    sleep(0.3);
  }
  sleep(1);
}

export function listTest() {
  let cursor = null;
  for (let i = 0; i < PAGES_PER_ITER; i++) {
    const url = cursor
      ? `${BASE_URL}/api/products?size=20&cursor=${encodeURIComponent(cursor)}`
      : `${BASE_URL}/api/products?size=20`;
    const res = http.get(url);

    const statusOk = check(res, { 'list status 200': (r) => r.status === 200 });
    if (!statusOk) break;

    const body = res.json();
    const hasContent = Array.isArray(body.content) && body.content.length > 0;
    check(res, { 'list has content': () => hasContent });
    if (!hasContent) break;

    if (!body.hasNext || !body.nextCursor) break;
    cursor = body.nextCursor;
    sleep(0.3);
  }
  sleep(1);
}

export default function () { searchTest(); }
