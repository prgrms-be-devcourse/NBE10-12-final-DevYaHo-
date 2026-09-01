import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// performance-test-data.sql 생성 데이터와 동일한 단어 배열
const ADJS  = ['유기농', '프리미엄', '베스트', '신선한', '천연', '국내산', '수입', '고품질', '특가', '한정'];
const NOUNS = ['비타민', '마스크', '텀블러', '에코백', '쿠션', '영양제', '셔츠', '신발', '가방', '모자'];

// 명사 10 + 형용사 10 + 전체 조합(10×10=100) = 120개 키워드 → 캐시 히트율 최소화
const KEYWORDS = [
  ...NOUNS,
  ...ADJS,
  ...ADJS.flatMap(a => NOUNS.map(n => `${a} ${n}`)),
];

const STAGES = [
  { duration: '30s', target: 10  },  // warm-up
  { duration: '1m',  target: 50  },  // 유지
  { duration: '30s', target: 100 },  // 증가
  { duration: '1m',  target: 100 },  // 피크 유지
  { duration: '30s', target: 0   },  // 감소
];

export const options = {
  scenarios: {
    // 시나리오 1: 검색 API (OpenSearch 전문 검색)
    search_api: {
      executor: 'ramping-vus',
      exec: 'searchTest',
      stages: STAGES,
      tags: { test_type: 'search' },
    },
    // 시나리오 2: 상품 목록 API (RDB 조회, 비교용)
    list_api: {
      executor: 'ramping-vus',
      exec: 'listTest',
      stages: STAGES,
      tags: { test_type: 'list' },
    },
  },
  thresholds: {
    'http_req_duration{test_type:search}': ['p(95)<3000', 'p(99)<5000'],
    'http_req_duration{test_type:list}':   ['p(95)<3000', 'p(99)<5000'],
    'http_req_failed':                     ['rate<0.05'],
  },
};

export function searchTest() {
  const kw   = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  // max_result_window=10000, size=20 → page 499까지 안전 (500*20=10000)
  const page = Math.floor(Math.random() * 500);
  const res  = http.get(
    `${BASE_URL}/api/products/search?keyword=${encodeURIComponent(kw)}&size=20&page=${page}`,
  );
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(1);
}

export function listTest() {
  const page = Math.floor(Math.random() * 1000);
  const res  = http.get(`${BASE_URL}/api/products?size=20&page=${page}`);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(1);
}

// --vus/--duration 커맨드라인 오버라이드 시 사용 (scenarios 우선)
export default function () {
  searchTest();
}
