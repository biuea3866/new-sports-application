/**
 * sw.js — PWA service worker
 *
 * 앱 셸(HTML)만 캐시해 오프라인·재방문 시 즉시 뜨게 합니다.
 * API 응답은 캐시하지 않습니다 — 서버 데이터의 오프라인 캐시는 이미
 * TanStack Query persist(localStorage)가 담당하고, 여기서 또 캐시하면
 * 두 계층이 서로 다른 신선도를 갖게 됩니다.
 */
const CACHE_NAME = 'sports-app-shell-v1';
const APP_SHELL = ['/', '/index.html'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;

  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  if (url.origin !== self.location.origin) return;

  // 네비게이션은 네트워크 우선, 실패하면 캐시된 앱 셸로 폴백합니다.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() =>
        caches.match('/index.html').then((cached) => cached ?? caches.match('/'))
      )
    );
    return;
  }

  // 정적 자산은 캐시 우선(해시 파일명이라 무효화 걱정이 없습니다).
  if (url.pathname.startsWith('/_expo/') || url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(request).then(
        (cached) =>
          cached ??
          fetch(request).then((response) => {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
            return response;
          })
      )
    );
  }
});
