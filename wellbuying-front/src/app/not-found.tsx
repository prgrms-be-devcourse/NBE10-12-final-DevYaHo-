import Link from "next/link";
import { SearchX } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
      <SearchX className="h-10 w-10 text-wb-green" strokeWidth={1.5} />
      <h2 className="text-lg font-bold">페이지를 찾을 수 없어요</h2>
      <p className="max-w-sm text-sm text-wb-secondary">
        주소가 잘못되었거나 삭제된 페이지예요.
      </p>
      <Link
        href="/home"
        className="mt-2 inline-flex h-11 items-center justify-center rounded-lg bg-wb-green px-5 text-sm font-semibold text-white transition-colors hover:bg-wb-green/90"
      >
        홈으로 이동
      </Link>
    </div>
  );
}
