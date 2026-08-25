"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";
import {
  SAMPLE_ADMIN_MEMBERS,
  SAMPLE_DEALS,
  SAMPLE_PARTICIPANT_COUNTS,
  SAMPLE_PRODUCER_DEAL_IDS,
  SAMPLE_SETTLEMENTS,
  SAMPLE_SUBMISSIONS,
} from "@/lib/mock/data";
import {
  activeTier,
  type AdminMember,
  type Deal,
  type DealStatus,
  type Order,
  type PaymentMethod,
  type Participation,
  type ProductSubmission,
  type ReviewStatus,
  type SettlementRecord,
  type SettlementStatus,
} from "@/lib/mock/types";

type DemoState = {
  deals: Deal[];
  participantCounts: Record<string, number>;
  favoriteIds: Set<string>;
  participations: Participation[];
  orders: Order[];
  dealStatuses: Record<string, DealStatus>;
  producerDealIds: Set<string>;
  submissions: ProductSubmission[];
  reviewStatuses: Record<string, ReviewStatus>;
  settlements: SettlementRecord[];
  settlementStatuses: Record<string, SettlementStatus>;
  lastBatchRun: string | null;
};

const SCHEDULED_DEAL_IDS = new Set(["deal-candle", "deal-scarf", "deal-yuja", "deal-campmat"]);

function initialState(): DemoState {
  return {
    deals: SAMPLE_DEALS,
    participantCounts: { ...SAMPLE_PARTICIPANT_COUNTS },
    favoriteIds: new Set(),
    participations: [],
    orders: [],
    dealStatuses: Object.fromEntries(
      SAMPLE_DEALS.map((deal) => [
        deal.id,
        (SCHEDULED_DEAL_IDS.has(deal.id) ? "scheduled" : "recruiting") as DealStatus,
      ]),
    ),
    producerDealIds: new Set(SAMPLE_PRODUCER_DEAL_IDS),
    submissions: SAMPLE_SUBMISSIONS,
    reviewStatuses: Object.fromEntries(SAMPLE_SUBMISSIONS.map((item) => [item.id, "pending" as ReviewStatus])),
    settlements: SAMPLE_SETTLEMENTS,
    settlementStatuses: Object.fromEntries(
      SAMPLE_SETTLEMENTS.map((item, index) => [item.id, index === 2 ? "held" : ("ready" as SettlementStatus)]),
    ),
    lastBatchRun: null,
  };
}

function newId(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

type DemoStoreValue = {
  deals: Deal[];
  dealStatuses: Record<string, DealStatus>;
  producerDealIds: Set<string>;
  favoriteIds: Set<string>;
  participations: Participation[];
  orders: Order[];
  submissions: ProductSubmission[];
  reviewStatuses: Record<string, ReviewStatus>;
  settlements: SettlementRecord[];
  settlementStatuses: Record<string, SettlementStatus>;
  adminMembers: AdminMember[];
  lastBatchRun: string | null;

  dealById: (id: string) => Deal | undefined;
  participants: (dealId: string) => number;
  visibleDeals: () => Deal[];
  scheduledDeals: () => Deal[];
  hasActiveParticipation: (dealId: string) => boolean;
  canCancelBeforeStart: (dealId: string) => boolean;

  toggleFavorite: (dealId: string) => void;
  participate: (dealId: string, quantity: number) => void;
  cancelParticipation: (participationId: string) => void;
  completeDeal: (dealId: string) => void;
  approvePayment: (participationId: string, method: PaymentMethod) => boolean;
  advanceDelivery: (orderId: string) => void;
  confirmPurchase: (orderId: string) => void;
  cancelOrRefund: (orderId: string) => void;

  setDealStatus: (dealId: string, status: DealStatus) => void;
  addProducerDeal: (deal: Deal, status: DealStatus) => void;
  updateProducerDeal: (deal: Deal) => void;

  setReview: (submissionId: string, status: ReviewStatus) => void;
  runSettlementBatch: () => void;

  pendingReviewCount: number;
  readySettlementCount: number;
  readySettlementAmount: number;
};

const DemoStoreContext = createContext<DemoStoreValue | null>(null);

export function DemoStoreProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<DemoState>(initialState);

  const dealById = useCallback((id: string) => state.deals.find((deal) => deal.id === id), [state.deals]);

  const participants = useCallback(
    (dealId: string) => state.participantCounts[dealId] ?? 0,
    [state.participantCounts],
  );

  const visibleDeals = useCallback(
    () => state.deals.filter((deal) => state.dealStatuses[deal.id] === "recruiting"),
    [state.deals, state.dealStatuses],
  );

  const scheduledDeals = useCallback(
    () => state.deals.filter((deal) => state.dealStatuses[deal.id] === "scheduled"),
    [state.deals, state.dealStatuses],
  );

  const hasActiveParticipation = useCallback(
    (dealId: string) =>
      state.participations.some((item) => item.dealId === dealId && item.state !== "cancelled"),
    [state.participations],
  );

  const canCancelBeforeStart = useCallback(
    (dealId: string) => {
      const status = state.dealStatuses[dealId] ?? "draft";
      return (status === "draft" || status === "scheduled") && participants(dealId) === 0;
    },
    [state.dealStatuses, participants],
  );

  const toggleFavorite = useCallback((dealId: string) => {
    setState((prev) => {
      const next = new Set(prev.favoriteIds);
      if (next.has(dealId)) next.delete(dealId);
      else next.add(dealId);
      return { ...prev, favoriteIds: next };
    });
  }, []);

  const participate = useCallback((dealId: string, quantity: number) => {
    setState((prev) => {
      const deal = prev.deals.find((item) => item.id === dealId);
      if (!deal) return prev;
      const alreadyActive = prev.participations.some(
        (item) => item.dealId === dealId && item.state !== "cancelled",
      );
      if (alreadyActive) return prev;

      const currentPeople = prev.participantCounts[dealId] ?? 0;
      const price = activeTier(deal, currentPeople + quantity).price;
      const participation: Participation = {
        id: newId("pt"),
        dealId,
        quantity,
        reservedPrice: price,
        joinedAt: new Date().toISOString(),
        state: "recruiting",
        finalPrice: null,
      };

      return {
        ...prev,
        participantCounts: { ...prev.participantCounts, [dealId]: currentPeople + quantity },
        participations: [participation, ...prev.participations],
      };
    });
  }, []);

  const cancelParticipation = useCallback((participationId: string) => {
    setState((prev) => {
      const target = prev.participations.find((item) => item.id === participationId);
      if (!target || target.state !== "recruiting") return prev;
      return {
        ...prev,
        participantCounts: {
          ...prev.participantCounts,
          [target.dealId]: Math.max(0, (prev.participantCounts[target.dealId] ?? 0) - target.quantity),
        },
        participations: prev.participations.map((item) =>
          item.id === participationId ? { ...item, state: "cancelled" } : item,
        ),
      };
    });
  }, []);

  const completeDeal = useCallback((dealId: string) => {
    setState((prev) => {
      const deal = prev.deals.find((item) => item.id === dealId);
      if (!deal) return prev;
      const finalPrice = activeTier(deal, prev.participantCounts[dealId] ?? 0).price;
      return {
        ...prev,
        participations: prev.participations.map((item) =>
          item.dealId === dealId && item.state === "recruiting"
            ? { ...item, reservedPrice: finalPrice, finalPrice, state: "paymentRequired" }
            : item,
        ),
        dealStatuses: { ...prev.dealStatuses, [dealId]: "completed" },
      };
    });
  }, []);

  const approvePayment = useCallback(
    (participationId: string, method: PaymentMethod) => {
      let success = false;
      setState((prev) => {
        const participation = prev.participations.find((item) => item.id === participationId);
        if (!participation || participation.state !== "paymentRequired") return prev;
        success = true;
        const price = participation.finalPrice ?? participation.reservedPrice;
        const order: Order = {
          id: newId("order"),
          orderNumber: `WB-${Math.floor(100000 + Math.random() * 900000)}`,
          participationId,
          dealId: participation.dealId,
          quantity: participation.quantity,
          unitPrice: price,
          paymentMethod: method,
          paymentState: "approved",
          deliveryState: "preparing",
          orderedAt: new Date().toISOString(),
        };
        return {
          ...prev,
          participations: prev.participations.map((item) =>
            item.id === participationId ? { ...item, state: "paid" } : item,
          ),
          orders: [order, ...prev.orders],
        };
      });
      return success;
    },
    [],
  );

  const advanceDelivery = useCallback((orderId: string) => {
    setState((prev) => ({
      ...prev,
      orders: prev.orders.map((order) => {
        if (order.id !== orderId || order.paymentState !== "approved") return order;
        if (order.deliveryState === "preparing") return { ...order, deliveryState: "shipping" };
        if (order.deliveryState === "shipping") return { ...order, deliveryState: "delivered" };
        return order;
      }),
    }));
  }, []);

  const confirmPurchase = useCallback((orderId: string) => {
    setState((prev) => ({
      ...prev,
      orders: prev.orders.map((order) =>
        order.id === orderId && order.paymentState === "approved" && order.deliveryState === "delivered"
          ? { ...order, deliveryState: "confirmed" }
          : order,
      ),
    }));
  }, []);

  const cancelOrRefund = useCallback((orderId: string) => {
    setState((prev) => ({
      ...prev,
      orders: prev.orders.map((order) => {
        if (order.id !== orderId) return order;
        if (order.deliveryState === "preparing") return { ...order, paymentState: "cancelled" };
        if (order.deliveryState !== "confirmed") return { ...order, paymentState: "refunded" };
        return order;
      }),
    }));
  }, []);

  const setDealStatus = useCallback((dealId: string, status: DealStatus) => {
    setState((prev) => ({ ...prev, dealStatuses: { ...prev.dealStatuses, [dealId]: status } }));
  }, []);

  const addProducerDeal = useCallback((deal: Deal, status: DealStatus) => {
    setState((prev) => ({
      ...prev,
      deals: [deal, ...prev.deals],
      participantCounts: { ...prev.participantCounts, [deal.id]: 0 },
      dealStatuses: { ...prev.dealStatuses, [deal.id]: status },
      producerDealIds: new Set(prev.producerDealIds).add(deal.id),
    }));
  }, []);

  const updateProducerDeal = useCallback((deal: Deal) => {
    setState((prev) => ({
      ...prev,
      deals: prev.deals.map((item) => (item.id === deal.id ? deal : item)),
    }));
  }, []);

  const setReview = useCallback((submissionId: string, status: ReviewStatus) => {
    setState((prev) => ({ ...prev, reviewStatuses: { ...prev.reviewStatuses, [submissionId]: status } }));
  }, []);

  const runSettlementBatch = useCallback(() => {
    setState((prev) => {
      const nextStatuses = { ...prev.settlementStatuses };
      for (const record of prev.settlements) {
        if (nextStatuses[record.id] === "ready") nextStatuses[record.id] = "completed";
      }
      return { ...prev, settlementStatuses: nextStatuses, lastBatchRun: new Date().toISOString() };
    });
  }, []);

  const pendingReviewCount = useMemo(
    () => Object.values(state.reviewStatuses).filter((status) => status === "pending").length,
    [state.reviewStatuses],
  );

  const readySettlementCount = useMemo(
    () => Object.values(state.settlementStatuses).filter((status) => status === "ready").length,
    [state.settlementStatuses],
  );

  const readySettlementAmount = useMemo(
    () =>
      state.settlements
        .filter((record) => state.settlementStatuses[record.id] === "ready")
        .reduce((total, record) => total + record.payout, 0),
    [state.settlements, state.settlementStatuses],
  );

  const value: DemoStoreValue = {
    deals: state.deals,
    dealStatuses: state.dealStatuses,
    producerDealIds: state.producerDealIds,
    favoriteIds: state.favoriteIds,
    participations: state.participations,
    orders: state.orders,
    submissions: state.submissions,
    reviewStatuses: state.reviewStatuses,
    settlements: state.settlements,
    settlementStatuses: state.settlementStatuses,
    adminMembers: SAMPLE_ADMIN_MEMBERS,
    lastBatchRun: state.lastBatchRun,

    dealById,
    participants,
    visibleDeals,
    scheduledDeals,
    hasActiveParticipation,
    canCancelBeforeStart,

    toggleFavorite,
    participate,
    cancelParticipation,
    completeDeal,
    approvePayment,
    advanceDelivery,
    confirmPurchase,
    cancelOrRefund,

    setDealStatus,
    addProducerDeal,
    updateProducerDeal,

    setReview,
    runSettlementBatch,

    pendingReviewCount,
    readySettlementCount,
    readySettlementAmount,
  };

  return <DemoStoreContext.Provider value={value}>{children}</DemoStoreContext.Provider>;
}

export function useDemoStore() {
  const context = useContext(DemoStoreContext);
  if (!context) throw new Error("useDemoStore must be used within DemoStoreProvider");
  return context;
}
