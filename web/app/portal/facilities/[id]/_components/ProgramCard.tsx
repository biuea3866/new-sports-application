/**
 * ProgramCard — 시설상품 1건 표시 (가격 위계 최상단, 정원·소요시간 보조 필드).
 */
import type { Program } from "@/lib/portal/types";

export interface ProgramCardProps {
  program: Program;
}

function formatPrice(price: number): string {
  return `${price.toLocaleString("ko-KR")}원`;
}

export function ProgramCard({ program }: ProgramCardProps) {
  return (
    <div
      className="rounded-md border bg-card p-4 space-y-1"
      aria-label={`${program.name} 상품 정보`}
    >
      <div className="flex items-baseline justify-between gap-2">
        <span className="font-semibold">{program.name}</span>
        <span className="text-lg font-bold text-primary">{formatPrice(program.price)}</span>
      </div>
      {program.description && (
        <p className="text-sm text-muted-foreground">{program.description}</p>
      )}
      <p className="text-xs text-muted-foreground">
        정원 {program.capacity} · {program.durationMinutes}분
      </p>
    </div>
  );
}
