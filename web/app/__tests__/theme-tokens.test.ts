/**
 * 시맨틱 토큰(success/warning) 정적 검증.
 * design-fe-web.md "테마 토큰 정의 표" — 상태·타입 배지용 success/warning 토큰을
 * globals.css(:root/.dark)와 tailwind.config.ts colors 매핑에 추가한다.
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

describe("시맨틱 토큰 success/warning", () => {
  it("라이트(:root) 모드에 success/warning 토큰이 지정된 HSL 값으로 정의된다", () => {
    const css = readGlobalsCss();
    const rootBlock = extractBlock(css, ":root");

    expect(rootBlock).toMatch(/--success:\s*142 71% 45%;/);
    expect(rootBlock).toMatch(/--success-foreground:\s*0 0% 100%;/);
    expect(rootBlock).toMatch(/--warning:\s*38 92% 50%;/);
    expect(rootBlock).toMatch(/--warning-foreground:\s*0 0% 100%;/);
  });

  it(".dark 모드에서 success/warning 토큰이 다크 값으로 치환된다", () => {
    const css = readGlobalsCss();
    const darkBlock = extractBlock(css, "\\.dark");

    expect(darkBlock).toMatch(/--success:\s*142 64% 42%;/);
    expect(darkBlock).toMatch(/--success-foreground:\s*0 0% 100%;/);
    expect(darkBlock).toMatch(/--warning:\s*38 84% 46%;/);
    expect(darkBlock).toMatch(/--warning-foreground:\s*0 0% 100%;/);
  });

  it("기존 토큰(background/primary/destructive)이 라이트·다크 모두 변경되지 않았다", () => {
    const css = readGlobalsCss();
    const rootBlock = extractBlock(css, ":root");
    const darkBlock = extractBlock(css, "\\.dark");

    expect(rootBlock).toMatch(/--background:\s*0 0% 100%;/);
    expect(rootBlock).toMatch(/--primary:\s*222\.2 47\.4% 11\.2%;/);
    expect(rootBlock).toMatch(/--destructive:\s*0 84\.2% 60\.2%;/);

    expect(darkBlock).toMatch(/--background:\s*222\.2 84% 4\.9%;/);
    expect(darkBlock).toMatch(/--primary:\s*210 40% 98%;/);
    expect(darkBlock).toMatch(/--destructive:\s*0 62\.8% 30\.6%;/);
  });

  it("tailwind.config.ts colors에 success/warning이 DEFAULT+foreground로 hsl(var(--...)) 매핑된다", () => {
    const config = readTailwindConfig();

    expect(config).toMatch(/success:\s*\{\s*DEFAULT:\s*"hsl\(var\(--success\)\)",\s*foreground:\s*"hsl\(var\(--success-foreground\)\)",\s*\}/);
    expect(config).toMatch(/warning:\s*\{\s*DEFAULT:\s*"hsl\(var\(--warning\)\)",\s*foreground:\s*"hsl\(var\(--warning-foreground\)\)",\s*\}/);
  });
});
