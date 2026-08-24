"use client";

import { useState } from "react";
import { Info } from "lucide-react";
import { DealArtwork } from "@/components/deal/DealArtwork";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Tag } from "@/components/ui/Tag";
import { TextField } from "@/components/ui/TextField";
import { useDemoStore } from "@/lib/mock/DemoStoreProvider";
import { tierProfit, won, type Deal } from "@/lib/mock/types";

const CATEGORIES = ["식품", "생활", "패션"];
const CATEGORY_ICON: Record<string, string> = { 식품: "leaf", 생활: "droplet", 패션: "shirt" };
const STEP_LABELS = ["상품 정보", "가격 설계", "일정과 목표", "미리보기"];

type TierInput = { people: number; price: number; cost: number };

function defaultTiers(deal?: Deal | null): [TierInput, TierInput, TierInput] {
  if (deal) {
    const sorted = [...deal.tiers].sort((a, b) => a.minimumPeople - b.minimumPeople);
    return [sorted[0], sorted[1], sorted[2]].map((t) => ({
      people: t.minimumPeople,
      price: t.price,
      cost: t.cost,
    })) as [TierInput, TierInput, TierInput];
  }
  return [
    { people: 50, price: 20000, cost: 15000 },
    { people: 200, price: 17000, cost: 12000 },
    { people: 500, price: 15000, cost: 10000 },
  ];
}

export function DealEditorModal({
  open,
  onClose,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  editing?: Deal | null;
}) {
  if (!open) return null;
  return <DealEditorForm onClose={onClose} editing={editing} />;
}

function DealEditorForm({
  onClose,
  editing,
}: {
  onClose: () => void;
  editing?: Deal | null;
}) {
  const { addProducerDeal, updateProducerDeal, setDealStatus, dealStatuses } = useDemoStore();
  const [newDealId] = useState(() => `deal-${Math.random().toString(36).slice(2, 9)}`);
  const [step, setStep] = useState(1);
  const [title, setTitle] = useState(editing?.title ?? "");
  const [category, setCategory] = useState(editing?.category ?? "식품");
  const [summary, setSummary] = useState(editing?.summary ?? "");
  const [detail, setDetail] = useState(editing?.detail ?? "");
  const [daysLeft, setDaysLeft] = useState(editing?.daysLeft ?? 7);
  const [targetPeople, setTargetPeople] = useState(editing?.targetPeople ?? 500);
  const [tiers, setTiers] = useState<[TierInput, TierInput, TierInput]>(() => defaultTiers(editing));

  function updateTier(index: number, patch: Partial<TierInput>) {
    setTiers((prev) => {
      const next = [...prev] as [TierInput, TierInput, TierInput];
      next[index] = { ...next[index], ...patch };
      return next;
    });
  }

  const draft: Deal = {
    id: editing?.id ?? newDealId,
    title: title || "새 공동구매 상품",
    producer: editing?.producer ?? "내 브랜드",
    category,
    summary: summary || "상품의 핵심 특징을 한 문장으로 소개해주세요.",
    detail: detail || "생산 과정과 이 상품을 만든 이유를 들려주세요.",
    icon: editing?.icon ?? CATEGORY_ICON[category],
    tint: editing?.tint ?? "herb",
    daysLeft: Math.max(1, daysLeft),
    targetPeople: Math.max(1, targetPeople),
    tiers: tiers.map((t) => ({
      minimumPeople: Math.max(1, t.people),
      price: Math.max(1, t.price),
      cost: Math.max(0, t.cost),
    })),
  };

  function save(publish: boolean) {
    if (editing) {
      updateProducerDeal(draft);
      setDealStatus(draft.id, publish ? "recruiting" : (dealStatuses[draft.id] ?? "draft"));
    } else {
      addProducerDeal(draft, publish ? "recruiting" : "draft");
    }
    onClose();
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? "공동구매 수정" : "새 공동구매 개설"}
      subtitle={`${step}/4 · ${STEP_LABELS[step - 1]}`}
      width="640px"
    >
      <div className="mb-6 flex gap-1.5">
        {[1, 2, 3, 4].map((n) => (
          <div key={n} className={`h-1 flex-1 rounded-full ${n <= step ? "bg-wb-green" : "bg-wb-line"}`} />
        ))}
      </div>

      {step === 1 && (
        <div className="space-y-4">
          <TextField label="상품명" placeholder="예: 산지 직송 유기농 토마토" value={title} onChange={(e) => setTitle(e.target.value)} />
          <div>
            <p className="mb-1.5 text-xs font-bold">카테고리</p>
            <div className="flex gap-2">
              {CATEGORIES.map((item) => (
                <button
                  key={item}
                  onClick={() => setCategory(item)}
                  className={`flex-1 rounded-lg py-2 text-sm font-semibold ${
                    category === item ? "bg-wb-green text-white" : "bg-wb-canvas text-wb-secondary"
                  }`}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>
          <TextField label="한 줄 소개" placeholder="상품의 장점을 짧게 알려주세요" value={summary} onChange={(e) => setSummary(e.target.value)} />
          <div>
            <p className="mb-1.5 text-xs font-bold">생산자 이야기</p>
            <textarea
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              rows={5}
              className="w-full rounded-lg border border-wb-line bg-wb-canvas p-3 text-sm outline-none focus:border-wb-green"
            />
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <p className="text-sm text-wb-secondary">수량이 늘어날수록 낮아지는 가격을 3단계로 설계해주세요.</p>
          {tiers.map((tier, index) => (
            <div key={index} className="rounded-xl bg-wb-canvas p-4">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-sm font-bold">{index + 1}단계</p>
                <p className="text-xs font-semibold text-wb-green">
                  예상 이익 {won(Math.max(0, tier.price - tier.cost))}
                </p>
              </div>
              <div className="grid grid-cols-3 gap-2.5">
                <NumberField label="시작 인원" suffix="명" value={tier.people} onChange={(v) => updateTier(index, { people: v })} />
                <NumberField label="판매가" suffix="원" value={tier.price} onChange={(v) => updateTier(index, { price: v })} />
                <NumberField label="공개 원가" suffix="원" value={tier.cost} onChange={(v) => updateTier(index, { cost: v })} />
              </div>
            </div>
          ))}
          <p className="flex items-center gap-1.5 text-xs text-wb-secondary">
            <Info className="h-3.5 w-3.5" /> 판매가에서 공개 원가를 뺀 금액이 생산자 이익으로 표시됩니다.
          </p>
        </div>
      )}

      {step === 3 && (
        <div className="space-y-4">
          <NumberCard title="모집 기간" detail="오늘부터 며칠 동안 모집할까요?" suffix="일" value={daysLeft} onChange={setDaysLeft} />
          <NumberCard title="목표 참여 인원" detail="달성 여부를 판단하는 기준이에요." suffix="명" value={targetPeople} onChange={setTargetPeople} />
          <div className="rounded-xl bg-wb-light-green/40 p-4 text-xs text-wb-secondary">
            공개하면 소비자 둘러보기에 즉시 나타납니다. 모집 중에는 일시 중지하거나 종료할 수 있고, 공개 전에는 취소할 수 있어요.
          </div>
        </div>
      )}

      {step === 4 && (
        <div className="space-y-5">
          <p className="text-sm font-bold">소비자에게 이렇게 보여요</p>
          <div className="grid gap-4 rounded-xl border border-wb-line p-4 md:grid-cols-2">
            <DealArtwork deal={draft} className="h-40 w-full" />
            <div>
              <div className="flex gap-2">
                <Tag>{draft.category}</Tag>
                <Tag highlighted>D-{draft.daysLeft}</Tag>
              </div>
              <p className="mt-2.5 line-clamp-2 text-lg font-bold">{draft.title}</p>
              <p className="mt-1 line-clamp-3 text-xs text-wb-secondary">{draft.summary}</p>
              <p className="mt-3 text-xs text-wb-secondary">{draft.tiers[0].minimumPeople}명부터</p>
              <p className="text-xl font-bold">{won(draft.tiers[0].price)}</p>
            </div>
          </div>
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
            {draft.tiers.map((tier) => (
              <div key={tier.minimumPeople} className="rounded-lg bg-wb-canvas p-3">
                <p className="text-xs text-wb-secondary">{tier.minimumPeople}명</p>
                <p className="text-sm font-bold">{won(tier.price)}</p>
                <p className="text-[11px] text-wb-secondary">
                  원가 {won(tier.cost)} · 이익 {won(tierProfit(tier))}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="mt-6 flex items-center justify-between border-t border-wb-line pt-5">
        {step > 1 ? (
          <Button variant="secondary" onClick={() => setStep((s) => s - 1)}>
            이전
          </Button>
        ) : (
          <span />
        )}
        {step < 4 ? (
          <Button disabled={step === 1 && title.length === 0} onClick={() => setStep((s) => s + 1)}>
            다음
          </Button>
        ) : (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => save(false)}>
              임시저장
            </Button>
            <Button onClick={() => save(true)}>{editing ? "변경 내용 저장" : "공동구매 공개"}</Button>
          </div>
        )}
      </div>
    </Modal>
  );
}

function NumberField({
  label,
  suffix,
  value,
  onChange,
}: {
  label: string;
  suffix: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-[10px] text-wb-secondary">{label}</span>
      <div className="flex items-center gap-1 rounded-lg border border-wb-line bg-wb-surface px-2.5 py-2">
        <input
          type="number"
          value={value}
          onChange={(e) => onChange(Number(e.target.value) || 0)}
          className="w-full bg-transparent text-sm font-bold outline-none"
        />
        <span className="shrink-0 text-[10px] text-wb-secondary">{suffix}</span>
      </div>
    </label>
  );
}

function NumberCard({
  title,
  detail,
  suffix,
  value,
  onChange,
}: {
  title: string;
  detail: string;
  suffix: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-wb-line bg-wb-canvas/60 p-4">
      <div>
        <p className="text-sm font-bold">{title}</p>
        <p className="text-xs text-wb-secondary">{detail}</p>
      </div>
      <div className="flex items-center gap-1.5 rounded-lg bg-wb-canvas px-3 py-2">
        <input
          type="number"
          value={value}
          onChange={(e) => onChange(Number(e.target.value) || 0)}
          className="w-16 bg-transparent text-right text-base font-bold outline-none"
        />
        <span className="text-xs text-wb-secondary">{suffix}</span>
      </div>
    </div>
  );
}
