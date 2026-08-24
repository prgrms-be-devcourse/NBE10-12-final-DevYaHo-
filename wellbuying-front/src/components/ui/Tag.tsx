export function Tag({
  children,
  highlighted = false,
}: {
  children: React.ReactNode;
  highlighted?: boolean;
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${
        highlighted
          ? "bg-wb-light-green/70 text-wb-green"
          : "bg-wb-tag-surface text-wb-secondary"
      }`}
    >
      {children}
    </span>
  );
}

export function StatusPill({
  children,
  tone = "neutral",
}: {
  children: React.ReactNode;
  tone?: "green" | "orange" | "red" | "blue" | "neutral";
}) {
  const tones: Record<string, string> = {
    green: "text-wb-green bg-wb-green/12",
    orange: "text-wb-orange bg-wb-orange/12",
    red: "text-red-600 bg-red-600/12 dark:text-red-400",
    blue: "text-blue-600 bg-blue-600/12 dark:text-blue-400",
    neutral: "text-wb-secondary bg-wb-secondary/12",
  };
  return (
    <span className={`inline-flex h-6 items-center rounded-full px-2.5 text-xs font-bold ${tones[tone]}`}>
      {children}
    </span>
  );
}
