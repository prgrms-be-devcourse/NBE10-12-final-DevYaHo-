// seller_info.account_number/account_holder는 SellerInfoFieldConverter(AES-256-GCM)로
// 애플리케이션 레벨에서 암복호화된다. SQL로 시드 데이터를 직접 넣을 때 평문을 그대로 넣으면
// 앱이 읽을 때 "Illegal base64 character" JpaSystemException으로 500이 난다 (issue: 회원관리 500 버그).
//
// 이 스크립트는 psql로 뽑은 "id|account_number(평문)|account_holder(평문)" 파이프 구분 텍스트를
// 읽어서, 앱과 동일한 키/알고리즘으로 암호화한 UPDATE 문 SQL 파일을 만들어준다.
//
// 사용법:
//   docker exec wellbuying-postgres psql -U postgres -d wellbuying -t -A -F'|' \
//     -c "SELECT id, account_number, account_holder FROM seller_info WHERE length(account_number) < 20 ORDER BY id;" \
//     > /tmp/seller_info_plain.csv
//   node scripts/encrypt-seller-info.js /tmp/seller_info_plain.csv /tmp/seller_info_encrypt.sql
//   docker exec -i wellbuying-postgres psql -U postgres -d wellbuying < /tmp/seller_info_encrypt.sql
//
// length(account_number) < 20 을 "아직 암호화 안 된 행" 판별 기준으로 쓰는 이유: 암호화된 값은
// IV(12바이트)+암호문+태그(16바이트)를 Base64로 인코딩하므로 항상 40자 이상이지만, 평문 계좌번호는
// 보통 10~20자 이내라 이 기준으로 안전하게 구분된다.
//
// 키는 SELLER_INFO_ENC_KEY 환경변수(운영과 동일한 방식)를 우선 쓰고, 없으면 application.yaml의
// 로컬 개발 기본값을 쓴다. 이미 정상 암호화된 행을 다시 암호화하면 복호화가 깨지므로, 반드시
// 위 SELECT의 길이 조건처럼 "아직 평문인 행"만 골라서 넘겨야 한다.
const fs = require("fs");
const crypto = require("crypto");

const KEY = Buffer.from(
  process.env.SELLER_INFO_ENC_KEY ?? "Q0hBTkdFX01FX2xvY2FsX2Rldl8zMl9ieXRlX2tleSE=",
  "base64",
);

function encrypt(plain) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", KEY, iv);
  const ciphertext = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, ciphertext, tag]).toString("base64");
}

function sqlEscape(s) {
  return s.replace(/'/g, "''");
}

const [, , inPath, outPath] = process.argv;
if (!inPath || !outPath) {
  console.error("usage: node encrypt-seller-info.js <plain.csv> <out.sql>");
  process.exit(1);
}

const lines = fs.readFileSync(inPath, "utf8").split("\n").filter((l) => l.trim().length > 0);

const statements = ["BEGIN;"];
for (const line of lines) {
  const [id, accountNumber, accountHolder] = line.split("|");
  const encNumber = encrypt(accountNumber);
  const encHolder = encrypt(accountHolder);
  statements.push(
    `UPDATE seller_info SET account_number = '${sqlEscape(encNumber)}', account_holder = '${sqlEscape(encHolder)}' WHERE id = ${id};`,
  );
}
statements.push("COMMIT;");

fs.writeFileSync(outPath, statements.join("\n") + "\n", "utf8");
console.log(`wrote ${lines.length} updates to ${outPath}`);
