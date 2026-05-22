import bundleAnalyzer from "@next/bundle-analyzer";

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env["ANALYZE"] === "true",
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  experimental: {
    turbo: {},
    // instrumentation.ts 활성화 (Next.js 14 — 서버 사이드 OpenTelemetry)
    instrumentationHook: true,
  },
};

export default withBundleAnalyzer(nextConfig);
