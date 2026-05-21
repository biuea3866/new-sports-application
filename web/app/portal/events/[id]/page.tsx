/**
 * Portal Events 단건 상세 페이지
 * Server Component wrapper — 실제 UI는 Client 컴포넌트
 */
import EventDetailClient from "./EventDetailClient";

interface PageProps {
  params: { id: string };
}

export function generateMetadata({ params }: PageProps) {
  return {
    title: `경기 상세 #${params.id} — Sports Portal`,
  };
}

export default function EventDetailPage({ params }: PageProps) {
  return (
    <main className="min-h-screen p-8 max-w-4xl mx-auto">
      <div className="mb-6">
        <a
          href="/portal/events"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          aria-label="내 경기 목록으로 돌아가기"
        >
          ← 내 경기 목록
        </a>
      </div>
      <EventDetailClient id={params.id} />
    </main>
  );
}
