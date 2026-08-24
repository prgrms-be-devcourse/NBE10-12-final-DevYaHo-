export function Card({
  className = "",
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`rounded-xl border border-wb-line bg-wb-surface p-6 ${className}`}
    >
      {children}
    </div>
  );
}
