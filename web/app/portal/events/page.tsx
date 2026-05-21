/**
 * Portal Events 목록 페이지
 * Server Component — 데이터 로딩은 Client 훅 위임
 */
import EventsListClient from "./EventsListClient";

export const metadata = {
  title: "내 경기 목록 — Sports Portal",
};

export default function EventsPage() {
  return (
    <main className="min-h-screen p-8 max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">내 경기 목록</h1>
        <a
          href="/portal/events/new"
          className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          경기 등록
        </a>
      </div>
      <EventsListClient />
    </main>
  );
}
