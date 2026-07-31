import Link from "next/link";

type AdminTopbarProps = {
  operatorName?: string;
};

export function AdminTopbar({ operatorName }: AdminTopbarProps): JSX.Element {
  return (
    <header
      aria-label="어드민 상단바"
      className="flex h-14 items-center justify-between border-b border-border bg-card px-6"
    >
      <Link href="/admin" className="text-sm font-semibold text-foreground hover:underline">
        Sports Admin
      </Link>
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        {operatorName !== undefined ? (
          <span aria-label="현재 운영자">{operatorName}</span>
        ) : (
          <span className="text-muted-foreground">미인증</span>
        )}
        <Link
          href="/admin/logout"
          className="rounded-md px-3 py-1.5 text-sm text-foreground hover:bg-accent focus:bg-accent focus:outline-none focus:ring-2 focus:ring-ring"
        >
          로그아웃
        </Link>
      </div>
    </header>
  );
}
