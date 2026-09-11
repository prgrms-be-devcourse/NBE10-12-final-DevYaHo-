"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { notFound, useParams } from "next/navigation";
import { GroupBuyArtwork } from "@/components/deal/GroupBuyArtwork";
import { Banner } from "@/components/ui/Banner";
import { ApiError } from "@/lib/api/http";
import { getProduct } from "@/lib/api/product";
import type { ProductDetailResponse } from "@/lib/api/types";
import { won } from "@/lib/format";
import { resolveCatalogEntry } from "@/lib/groupBuy/seedCatalog";

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const productId = Number(params.id);

  const [product, setProduct] = useState<ProductDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [resourceNotFound, setResourceNotFound] = useState(false);
  const [thumbnailFailed, setThumbnailFailed] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(productId)) return;
    let ignore = false;

    async function load() {
      setLoading(true);
      setLoadError(null);
      setThumbnailFailed(false);
      try {
        const res = await getProduct(productId);
        if (!ignore) setProduct(res);
      } catch (e) {
        if (ignore) return;
        if (e instanceof ApiError && e.status === 404) {
          setResourceNotFound(true);
        } else {
          setLoadError(e instanceof ApiError ? e.message : "상품 정보를 불러오지 못했어요.");
        }
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [productId]);

  if (!Number.isFinite(productId) || resourceNotFound) {
    notFound();
  }

  if (loading) {
    return <div className="p-9 text-sm text-wb-secondary">불러오는 중...</div>;
  }

  if (loadError || !product) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-9">
        <Banner tone="error">{loadError ?? "상품 정보를 불러오지 못했어요."}</Banner>
      </div>
    );
  }

  const catalog = resolveCatalogEntry(product.productName);

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-6 py-9">
      {product.thumbnailUrl && !thumbnailFailed ? (
        // eslint-disable-next-line @next/next/no-img-element -- 판매자가 등록한 외부 썸네일 URL이라 next/image 최적화 대상이 아님
        <img
          src={product.thumbnailUrl}
          alt={product.productName}
          className="aspect-[4/3] w-full rounded-2xl object-cover"
          onError={() => setThumbnailFailed(true)}
        />
      ) : (
        <GroupBuyArtwork entry={catalog} className="aspect-[4/3] w-full" />
      )}

      <div className="space-y-1">
        <p className="text-xs text-wb-secondary">{catalog.producerName}</p>
        <h1 className="text-2xl">{product.productName}</h1>
        <p className="text-xl font-bold">{won(product.startPrice)}</p>
      </div>

      {!product.available && <Banner tone="error">현재 구매할 수 없는 상품이에요.</Banner>}

      {(product.description || catalog.detail) && (
        <p className="text-sm text-wb-secondary">{product.description || catalog.detail}</p>
      )}

      <div className="rounded-2xl border border-wb-line bg-wb-canvas p-4 text-sm text-wb-secondary">
        아직 진행 중인 공동구매가 없어요. 공동구매가 열리면 이 페이지에서 안내해드릴게요.
      </div>

      <Link href="/explore" className="text-sm font-semibold text-wb-green">
        ← 둘러보기로 돌아가기
      </Link>
    </div>
  );
}
