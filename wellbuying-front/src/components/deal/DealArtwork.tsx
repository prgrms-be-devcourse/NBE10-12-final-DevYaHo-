import { Coffee, Droplet, Gift, Leaf, Package, Palette, Shirt, Sparkles, type LucideIcon } from "lucide-react";
import { TINT_GRADIENTS, type Deal } from "@/lib/mock/types";

const ICONS: Record<string, LucideIcon> = {
  droplet: Droplet,
  leaf: Leaf,
  shirt: Shirt,
  coffee: Coffee,
  gift: Gift,
  package: Package,
  palette: Palette,
  sparkles: Sparkles,
};

export function DealArtwork({ deal, className = "" }: { deal: Deal; className?: string }) {
  const Icon = ICONS[deal.icon] ?? Leaf;
  return (
    <div
      className={`relative flex items-center justify-center overflow-hidden rounded-2xl ${className}`}
      style={{ background: TINT_GRADIENTS[deal.tint] }}
    >
      <Icon className="h-16 w-16 text-wb-green/70" strokeWidth={1.25} />
    </div>
  );
}
