#!/usr/bin/env node
/**
 * PostgreSQL → OpenSearch 벌크 색인 스크립트 (Node.js)
 * 사용: node opensearch-bulk-index.mjs [--batch 5000]
 * 필요 패키지: npm install pg node-fetch (또는 내장 fetch 사용)
 */

import { createRequire } from "module";
const require = createRequire(import.meta.url);

const PG_CONFIG = {
  host: "localhost",
  port: 5432,
  database: "wellbuying",
  user: "postgres",
  password: "postgres",
};
const OS_URL = "http://localhost:9200";
const INDEX_NAME = "search_product_document";
const BATCH_SIZE = parseInt(process.argv.find((a, i) => process.argv[i - 1] === "--batch") ?? "5000");

function formatDatetime(dt) {
  // date_hour_minute_second_millis: yyyy-MM-dd'T'HH:mm:ss.SSS
  if (!dt) return null;
  const d = new Date(dt);
  const pad = (n, w = 2) => String(n).padStart(w, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
  );
}

async function bulkIndex(docs) {
  const lines = [];
  for (const doc of docs) {
    lines.push(JSON.stringify({ index: { _index: INDEX_NAME, _id: String(doc.id) } }));
    lines.push(JSON.stringify(doc));
  }
  const body = lines.join("\n") + "\n";
  const resp = await fetch(`${OS_URL}/_bulk`, {
    method: "POST",
    headers: { "Content-Type": "application/x-ndjson" },
    body,
  });
  if (!resp.ok) throw new Error(`_bulk failed: ${resp.status} ${await resp.text()}`);
  const result = await resp.json();
  const errors = (result.items ?? []).filter((item) => item.index?.error).length;
  return { took: result.took, errors };
}

async function main() {
  let pg;
  try {
    pg = require("pg");
  } catch {
    console.error("pg 패키지가 없습니다. 설치: npm install pg");
    process.exit(1);
  }

  const client = new pg.Client(PG_CONFIG);
  await client.connect();

  const query = `
    SELECT
      p.id,
      p.product_name,
      p.description,
      p.category_id,
      p.status,
      p.start_price,
      COALESCE(pc.view_count, 0) AS view_count,
      p.thumbnail_url,
      p.seller_id,
      p.created_at
    FROM product p
    LEFT JOIN product_count pc ON pc.product_id = p.id
    ORDER BY p.id
  `;

  console.log("PostgreSQL 쿼리 실행 중...");
  const res = await client.query(query);
  await client.end();

  const rows = res.rows;
  console.log(`조회 완료: ${rows.length.toLocaleString()}건`);

  let total = 0;
  let errorTotal = 0;
  const start = Date.now();

  for (let i = 0; i < rows.length; i += BATCH_SIZE) {
    const batchRows = rows.slice(i, i + BATCH_SIZE);
    const docs = batchRows.map((r) => ({
      id: Number(r.id),
      productName: r.product_name,
      description: r.description,
      categoryId: Number(r.category_id),
      status: r.status,
      startPrice: r.start_price,
      viewCount: Number(r.view_count),
      thumbnailUrl: r.thumbnail_url,
      sellerId: Number(r.seller_id),
      createdAt: formatDatetime(r.created_at),
    }));

    const result = await bulkIndex(docs);
    total += docs.length;
    errorTotal += result.errors;
    const elapsed = ((Date.now() - start) / 1000).toFixed(1);
    const now = new Date().toTimeString().slice(0, 8);
    console.log(`[${now}] 색인: ${total.toLocaleString()}건 | 오류: ${errorTotal} | 경과: ${elapsed}s`);
  }

  const elapsed = ((Date.now() - start) / 1000).toFixed(1);
  console.log(`\n완료: ${total.toLocaleString()}건 색인 | 오류: ${errorTotal}건 | 총 소요: ${elapsed}s`);
}

main().catch((e) => { console.error(e); process.exit(1); });
