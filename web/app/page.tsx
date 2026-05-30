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
          aria-label="운영자 포털"
          className="rounded-lg border border-gray-200 p-6 text-left space-y-3"
        >
          <h2 className="text-lg font-semibold">운영자 포털 (데모)</h2>
          <p className="text-sm text-gray-500">
            로컬 데모용 자동 로그인입니다. 아래 버튼을 누르면 demo 운영자 계정으로 로그인해 포털
            대시보드로 이동합니다.
          </p>
          <a
            href="/dev-login"
            className="inline-block rounded-md bg-black px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            포털 들어가기 (데모 로그인)
          </a>
        </section>

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
