export default function Home() {
  const appName = process.env["NEXT_PUBLIC_APP_NAME"] ?? "Sports Application";

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-8">
      <div className="max-w-2xl w-full space-y-8 text-center">
        <h1 className="text-4xl font-bold tracking-tight">{appName}</h1>
        <p className="text-lg text-gray-600">
          생활 체육 플랫폼 — 시설 예약, 경기 티켓, 스포츠 물품 구매
        </p>

        <section
          aria-label="헬스 체크"
          className="rounded-lg border border-gray-200 p-6 text-left space-y-2"
        >
          <h2 className="text-lg font-semibold">시스템 상태</h2>
          <p className="text-sm text-gray-500">
            서비스 상태를 확인하려면{" "}
            <code className="rounded bg-gray-100 px-1 py-0.5 text-xs">/api/health</code> 엔드포인트를
            조회하세요.
          </p>
        </section>
      </div>
    </main>
  );
}
