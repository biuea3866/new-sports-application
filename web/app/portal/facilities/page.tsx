/**
 * Portal Facilities 목록 페이지
 * Server Component — 데이터 로딩은 Client Component에 위임
 */
import FacilitiesListClient from "./FacilitiesListClient";

export const metadata = {
  title: "내 시설 관리 — Sports Portal",
};

export default function FacilitiesPage() {
  return (
    <main className="max-w-5xl mx-auto px-4 py-8 space-y-6">
      <FacilitiesListClient />
    </main>
  );
}
