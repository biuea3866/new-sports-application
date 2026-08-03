/**
 * 파트너포털 테마 토큰 가드.
 *
 * 다크 모드에서 KPI 카드·달력 그리드·표 헤더가 흰 배경으로 남아 흰 글씨가 판독 불가였다.
 * 원인은 화면별 실수가 아니라 이 화면들이 시맨틱 토큰 대신 Tailwind 원색 팔레트
 * (`bg-white`·`text-gray-500`·`bg-gray-50` 등)를 직접 쓴 것이다 — 원색 팔레트는 `.dark`
 * 전환에 반응하지 않으므로 라이트 모드 한 벌만 구현한 셈이 된다.
 *
 * 화면마다 색을 덧칠하는 대신, 포털 전체에서 원색 팔레트 사용을 금지해 같은 결함이
 * 다시 들어오지 못하게 막는다. 색은 `globals.css`가 라이트/다크 두 벌로 정의한 시맨틱
 * 토큰(background·card·muted·border·status-* 등)으로만 쓴다.
 */
import { describe, it, expect } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

const PORTAL_ROOT = path.resolve(__dirname, "..");

const TAILWIND_PALETTE_NAMES = [
  "slate", "gray", "zinc", "neutral", "stone",
  "red", "orange", "amber", "yellow", "lime", "green", "emerald", "teal",
  "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose",
].join("|");

/** `bg-gray-50`·`text-red-600`·`divide-gray-100` 처럼 원색 팔레트에 눈금을 붙여 쓴 유틸리티. */
const PALETTE_SCALE_CLASS = new RegExp(
  String.raw`\b(?:bg|text|border|ring|divide|outline|fill|stroke|from|via|to)-(?:${TAILWIND_PALETTE_NAMES})-\d{2,3}\b`,
  "g"
);

/**
 * `bg-white`·`text-black` 처럼 모드와 무관하게 고정되는 **불투명** 절대색 유틸리티.
 *
 * 예외: `bg-black/50` 같은 **반투명 검정은 스크림(모달 오버레이) 전용**으로 허용한다.
 * 스크림은 모드에 따라 뒤집히면 안 되는 색이다 — 토큰으로 바꾸면 다크에서 흰 막이 씌워져
 * 다이얼로그가 스크림보다 어두워진다. 불투명 절대색만 계속 막는다(`/숫자`가 없는 경우).
 */
const ABSOLUTE_COLOR_CLASS =
  /\b(?:bg|text|border|ring|divide|fill|stroke)-(?:white|black)\b(?!\/\d)/g;

/** `#fff`·`rgb(...)`·`hsl(...)` 리터럴 — 토큰을 우회한 직접 색 지정. */
const RAW_COLOR_LITERAL = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/g;

function collectSourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const fullPath = path.join(directory, entry);
    if (statSync(fullPath).isDirectory()) {
      if (entry === "__tests__") return [];
      return collectSourceFiles(fullPath);
    }
    if (!/\.tsx?$/.test(entry)) return [];
    return [fullPath];
  });
}

function findViolations(pattern: RegExp): string[] {
  return collectSourceFiles(PORTAL_ROOT).flatMap((filePath) => {
    const relativePath = path.relative(PORTAL_ROOT, filePath);
    return readFileSync(filePath, "utf8")
      .split("\n")
      .flatMap((line, index) => {
        const matches = line.match(pattern) ?? [];
        return matches.map((match) => `${relativePath}:${index + 1} ${match}`);
      });
  });
}

describe("모달 스크림", () => {
  /*
   * 스크림(모달 뒤를 덮는 막)은 **모드에 따라 뒤집히면 안 되는 색**이다. 시맨틱 토큰을 쓰면
   * 다크에서 --foreground가 거의 흰색이라 화면 전체가 흰 막으로 덮이고, 그 위의 bg-background
   * 다이얼로그가 스크림보다 어두워진다. 레포의 다른 오버레이(dialog.tsx·admin 모달)도 전부
   * 반투명 검정을 유지하므로 포털만 다르면 일관성도 깨진다.
   */
  const overlayFiles = [
    "facilities/[id]/page.tsx",
    "products/[id]/page.tsx",
  ];

  it("스크림에 모드 반전되는 시맨틱 토큰을 쓰지 않는다", () => {
    const violations = overlayFiles.flatMap((relativePath) => {
      const source = readFileSync(path.join(PORTAL_ROOT, relativePath), "utf8");
      return source.includes("bg-foreground/") ? [relativePath] : [];
    });
    expect(violations).toEqual([]);
  });

  it("스크림은 반투명 검정을 유지한다", () => {
    const missing = overlayFiles.flatMap((relativePath) => {
      const source = readFileSync(path.join(PORTAL_ROOT, relativePath), "utf8");
      return /bg-black\/\d{1,3}\b/.test(source) ? [] : [relativePath];
    });
    expect(missing).toEqual([]);
  });
});

describe("네이티브 폼 컨트롤 테마", () => {
  /*
   * 날짜 입력의 달력 아이콘·스피너·스크롤바는 브라우저가 직접 그리며, 어떤 색으로 그릴지는
   * CSS `color-scheme`이 정한다. 선언이 없으면 브라우저는 light로 가정해 어두운 아이콘을
   * 칠하고, 다크 배경 위에서 검은 사각형으로 뭉개진다. 토큰만으로는 고칠 수 없는 영역이라
   * 루트에 color-scheme을 명시한다.
   */
  const globalsCss = readFileSync(path.resolve(PORTAL_ROOT, "..", "globals.css"), "utf8");

  it("라이트 모드에 color-scheme: light를 선언한다", () => {
    expect(globalsCss).toMatch(/:root\s*\{[^}]*color-scheme:\s*light/s);
  });

  it("다크 모드에 color-scheme: dark를 선언한다", () => {
    expect(globalsCss).toMatch(/\.dark\s*\{[^}]*color-scheme:\s*dark/s);
  });
});

describe("파트너포털 테마 토큰", () => {
  it("Tailwind 원색 팔레트 클래스를 쓰지 않는다", () => {
    expect(findViolations(PALETTE_SCALE_CLASS)).toEqual([]);
  });

  it("bg-white·text-black 같은 절대색 클래스를 쓰지 않는다", () => {
    expect(findViolations(ABSOLUTE_COLOR_CLASS)).toEqual([]);
  });

  it("색상 리터럴(#fff·rgb()·hsl())을 직접 쓰지 않는다", () => {
    expect(findViolations(RAW_COLOR_LITERAL)).toEqual([]);
  });
});
