import FacilitiesImportClient from "./FacilitiesImportClient";

export const metadata = {
  title: "시설 일괄 등록 — 사업자 포털",
};

export default function FacilitiesImportPage() {
  return (
    <main className="max-w-4xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight">시설 일괄 등록 (CSV Import)</h1>
        <p className="text-sm text-muted-foreground mt-1">
          CSV 파일을 업로드해 여러 시설을 한 번에 등록합니다.
        </p>
      </div>
      <FacilitiesImportClient />
    </main>
  );
}
