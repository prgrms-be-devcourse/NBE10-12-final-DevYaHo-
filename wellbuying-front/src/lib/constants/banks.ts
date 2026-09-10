export type Bank = { code: string; name: string };

export const BANKS: Bank[] = [
  { code: "004", name: "KB국민은행" },
  { code: "088", name: "신한은행" },
  { code: "020", name: "우리은행" },
  { code: "081", name: "하나은행" },
  { code: "011", name: "NH농협은행" },
  { code: "003", name: "IBK기업은행" },
  { code: "023", name: "SC제일은행" },
  { code: "027", name: "한국씨티은행" },
  { code: "032", name: "부산은행" },
  { code: "034", name: "광주은행" },
  { code: "035", name: "제주은행" },
  { code: "037", name: "전북은행" },
  { code: "039", name: "경남은행" },
  { code: "031", name: "iM뱅크(대구은행)" },
  { code: "045", name: "새마을금고" },
  { code: "048", name: "신협" },
  { code: "071", name: "우체국" },
  { code: "089", name: "케이뱅크" },
  { code: "090", name: "카카오뱅크" },
  { code: "092", name: "토스뱅크" },
];

export function bankNameByCode(code: string): string {
  return BANKS.find((bank) => bank.code === code)?.name ?? "";
}

export const BANK_OPTIONS = BANKS.map((bank) => ({ value: bank.code, label: bank.name }));
