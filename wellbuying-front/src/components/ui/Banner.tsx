type BannerProps = {
  tone?: "error" | "success";
  children: React.ReactNode;
};

const tones = {
  error: "bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-900",
  success:
    "bg-wb-light-green/60 text-wb-green border-wb-green/30",
};

export function Banner({ tone = "error", children }: BannerProps) {
  return (
    <div className={`rounded-lg border px-3 py-2.5 text-sm ${tones[tone]}`}>
      {children}
    </div>
  );
}
