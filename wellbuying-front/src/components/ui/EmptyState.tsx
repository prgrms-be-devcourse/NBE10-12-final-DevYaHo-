import type { LucideIcon } from "lucide-react";

export function EmptyState({
  icon: Icon,
  title,
  message,
}: {
  icon: LucideIcon;
  title: string;
  message: string;
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
      <Icon className="h-10 w-10 text-wb-green" strokeWidth={1.5} />
      <h2 className="text-lg font-bold">{title}</h2>
      <p className="max-w-sm text-sm text-wb-secondary">{message}</p>
    </div>
  );
}
