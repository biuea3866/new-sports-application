/**
 * +html.tsx — 웹 전용 HTML 셸 (Expo Router)
 *
 * 웹 빌드에만 적용되며 네이티브 번들에는 포함되지 않습니다.
 * PWA 설치 요건(manifest·apple-touch-icon·service worker 등록)을 여기서 채웁니다.
 * 색은 하드코딩하지 않고 theme/tokens의 시맨틱 토큰을 그대로 씁니다 —
 * 브라우저 UI(주소창) 색이 라이트/다크에서 앱 배경과 어긋나지 않도록
 * prefers-color-scheme별로 theme-color를 각각 선언합니다.
 */
import { ScrollViewStyleReset } from 'expo-router/html';
import type { PropsWithChildren } from 'react';

import { darkTokens, lightTokens } from '../theme/tokens';

const serviceWorkerRegistration = `
if ('serviceWorker' in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').catch(function (error) {
      console.warn('service worker 등록 실패', error);
    });
  });
}
`;

export default function Root({ children }: PropsWithChildren) {
  return (
    <html lang="ko">
      <head>
        <meta charSet="utf-8" />
        <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
        <meta
          name="viewport"
          content="width=device-width, initial-scale=1, shrink-to-fit=no, viewport-fit=cover"
        />

        <link rel="manifest" href="/manifest.json" />
        <meta
          name="theme-color"
          media="(prefers-color-scheme: light)"
          content={lightTokens.background}
        />
        <meta
          name="theme-color"
          media="(prefers-color-scheme: dark)"
          content={darkTokens.background}
        />

        <link rel="apple-touch-icon" href="/icons/icon-192.png" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="default" />
        <meta name="apple-mobile-web-app-title" content="SportsApp" />

        {/* 웹 body 스크롤 리셋 — RN ScrollView와 스크롤 동작을 맞춥니다. */}
        <ScrollViewStyleReset />

        <script dangerouslySetInnerHTML={{ __html: serviceWorkerRegistration }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
