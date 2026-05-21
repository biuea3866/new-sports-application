/**
 * Portal Events 등록 페이지
 * Server Component wrapper — 실제 폼은 Client 컴포넌트
 */
import EventCreateForm from "./EventCreateForm";

export const metadata = {
  title: "경기 등록 — Sports Portal",
};

export default function NewEventPage() {
  return (
    <main className="min-h-screen p-8 max-w-2xl mx-auto">
      <div className="mb-6">
        <a
          href="/portal/events"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          aria-label="내 경기 목록으로 돌아가기"
        >
          ← 내 경기 목록
        </a>
        <h1 className="text-2xl font-bold mt-2">경기 등록</h1>
      </div>
      <EventCreateForm />
    </main>
  );
}
