/**
 * 대기질 등급 시맨틱 토큰(--aq-*) 정적 검증.
 * design-fe-web.md "테마 토큰 — 대기질 등급" 표 값을 globals.css(:root/.dark)와
 * tailwind.config.ts colors 매핑에 추가한다.
 * CSS 커스텀 프로퍼티는 빌드 파이프라인 없이 jsdom에서 검증할 수 없으므로,
 * 소스 파일을 정적으로 읽어 토큰 선언 형식(HSL 채널 값)을 단언한다.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import path from "path";

function readGlobalsCss(): string {
  return readFileSync(path.resolve(__dirname, "../globals.css"), "utf-8");
}

function readTailwindConfig(): string {
  return readFileSync(path.resolve(__dirname, "../../tailwind.config.ts"), "utf-8");
}

function extractBlock(css: string, selector: string): string {
  const pattern = new RegExp(`${selector}\\s*\\{([^}]*)\\}`);
  const match = css.match(pattern);
  return match?.[1] ?? "";
}

describe("대기질 등급 시맨틱 토큰(--aq-*)", () => {
  it("라이트(:root) 모드에 좋음/보통/나쁨/매우나쁨/정보없음 토큰이 지정된 HSL 값으로 정의된다", () => {
    const css = readGlobalsCss();
    const rootBlock = extractBlock(css, ":root");

    expect(rootBlock).toMatch(/--aq-good:\s*211 100% 96%;/);
    expect(rootBlock).toMatch(/--aq-good-foreground:\s*211 90% 35%;/);
    expect(rootBlock).toMatch(/--aq-moderate:\s*142 60% 94%;/);
    expect(rootBlock).toMatch(/--aq-moderate-foreground:\s*142 70% 28%;/);
    expect(rootBlock).toMatch(/--aq-bad:\s*35 100% 94%;/);
    expect(rootBlock).toMatch(/--aq-bad-foreground:\s*30 90% 40%;/);
    expect(rootBlock).toMatch(/--aq-verybad:\s*0 90% 96%;/);
    expect(rootBlock).toMatch(/--aq-verybad-foreground:\s*0 75% 45%;/);
    expect(rootBlock).toMatch(/--aq-unknown:\s*210 16% 93%;/);
    expect(rootBlock).toMatch(/--aq-unknown-foreground:\s*215 16% 47%;/);
  });

  it(".dark 모드에서 좋음/보통/나쁨/매우나쁨/정보없음 토큰이 다크 값으로 치환된다", () => {
    const css = readGlobalsCss();
    const darkBlock = extractBlock(css, "\\.dark");

    expect(darkBlock).toMatch(/--aq-good:\s*211 60% 20%;/);
    expect(darkBlock).toMatch(/--aq-good-foreground:\s*211 90% 80%;/);
    expect(darkBlock).toMatch(/--aq-moderate:\s*142 40% 18%;/);
    expect(darkBlock).toMatch(/--aq-moderate-foreground:\s*142 60% 75%;/);
    expect(darkBlock).toMatch(/--aq-bad:\s*30 60% 20%;/);
    expect(darkBlock).toMatch(/--aq-bad-foreground:\s*35 90% 70%;/);
    expect(darkBlock).toMatch(/--aq-verybad:\s*0 55% 22%;/);
    expect(darkBlock).toMatch(/--aq-verybad-foreground:\s*0 85% 78%;/);
    expect(darkBlock).toMatch(/--aq-unknown:\s*217 20% 22%;/);
    expect(darkBlock).toMatch(/--aq-unknown-foreground:\s*215 20% 65%;/);
  });

  it("기존 토큰(background/success/warning)이 라이트·다크 모두 변경되지 않았다", () => {
    const css = readGlobalsCss();
    const rootBlock = extractBlock(css, ":root");
    const darkBlock = extractBlock(css, "\\.dark");

    expect(rootBlock).toMatch(/--background:\s*0 0% 100%;/);
    expect(rootBlock).toMatch(/--success:\s*142 71% 45%;/);
    expect(darkBlock).toMatch(/--background:\s*222\.2 84% 4\.9%;/);
    expect(darkBlock).toMatch(/--success:\s*142 64% 42%;/);
  });

  it("tailwind.config.ts colors에 aq-good/aq-moderate/aq-bad/aq-verybad/aq-unknown이 hsl(var(--aq-*)) 로 매핑된다", () => {
    const config = readTailwindConfig();

    expect(config).toMatch(/"aq-good":\s*"hsl\(var\(--aq-good\)\)"/);
    expect(config).toMatch(/"aq-good-foreground":\s*"hsl\(var\(--aq-good-foreground\)\)"/);
    expect(config).toMatch(/"aq-moderate":\s*"hsl\(var\(--aq-moderate\)\)"/);
    expect(config).toMatch(/"aq-moderate-foreground":\s*"hsl\(var\(--aq-moderate-foreground\)\)"/);
    expect(config).toMatch(/"aq-bad":\s*"hsl\(var\(--aq-bad\)\)"/);
    expect(config).toMatch(/"aq-bad-foreground":\s*"hsl\(var\(--aq-bad-foreground\)\)"/);
    expect(config).toMatch(/"aq-verybad":\s*"hsl\(var\(--aq-verybad\)\)"/);
    expect(config).toMatch(/"aq-verybad-foreground":\s*"hsl\(var\(--aq-verybad-foreground\)\)"/);
    expect(config).toMatch(/"aq-unknown":\s*"hsl\(var\(--aq-unknown\)\)"/);
    expect(config).toMatch(/"aq-unknown-foreground":\s*"hsl\(var\(--aq-unknown-foreground\)\)"/);
  });
});
