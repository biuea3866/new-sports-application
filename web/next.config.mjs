import bundleAnalyzer from "@next/bundle-analyzer";

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env["ANALYZE"] === "true",
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  experimental: {
    turbo: {},
    // Next 14.2에서 instrumentation.ts(OTel 등록 진입점)를 로드하려면 명시 활성화가 필요하다.
    // 근거: design-fe-web.md "AS-IS" — instrumentationHook 미설정 시 instrumentation.ts가 로드되지 않는다.
    instrumentationHook: true,
  },
};

export default withBundleAnalyzer(nextConfig);
