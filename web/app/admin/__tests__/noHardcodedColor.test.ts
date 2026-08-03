/**
 * 어드민 콘솔 **전체 트리** 하드코딩 색 금지 가드 (no-hardcoded-color · no-single-mode).
 *
 * 기존 가드는 feature-flags 하위 일부 파일만 화이트리스트로 검사해,
 * MCP 페이지·어드민 홈이 raw Tailwind 팔레트(`text-gray-900`·`bg-white`)로 작성된 것을 놓쳤다.
 * 그 결과 다크 모드에서 제목·카드·표 헤더가 판독 불가였다(01·06~10 캡쳐).
 *
 * 이 가드는 화이트리스트가 아니라 `app/admin/**` 를 **디렉토리 순회로 전수 검사**한다.
 * 새 어드민 화면이 추가돼도 자동으로 검사 대상에 포함된다.
 */
import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync } from "fs";
import { join, relative } from "path";

const ADMIN_ROOT = join(__dirname, "..");

/** Tailwind 기본 팔레트 색상 이름 — 시맨틱 토큰이 아니므로 다크 모드에서 반전되지 않는다. */
const PALETTE =
  "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose";

/** 색을 지정하는 Tailwind 유틸리티 접두사. variant(`hover:`·`dark:` 등)도 함께 잡는다. */
const COLOR_UTILITIES =
  "bg|text|border|ring|ring-offset|divide|from|via|to|fill|stroke|outline|decoration|shadow|accent|caret|placeholder";

const RULES: ReadonlyArray<{ name: string; pattern: RegExp }> = [
  {
    name: "hex 색상 리터럴",
    pattern: new RegExp("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\\b", "g"),
  },
  {
    name: "rgb()/rgba()/hsl() 리터럴",
    pattern: /\b(?:rgba?|hsla?)\(\s*\d/g,
  },
  {
    name: "Tailwind 기본 팔레트 클래스",
    pattern: new RegExp(`\\b(?:${COLOR_UTILITIES})-(?:${PALETTE})-\\d{2,3}\\b`, "g"),
  },
  {
    name: "bg-white/text-white/text-black 직접 지정",
    pattern: /\b(?:bg|text|border|divide|fill|stroke)-(?:white|black)\b(?!\/)/g,
  },
  {
    // 배경 토큰을 글자색으로 쓰면 같은 배경 위에서 글자가 사라진다.
    // (`bg-muted` 패널에 `text-muted` → 코드 블록 본문이 통째로 보이지 않던 회귀)
    // 글자색은 반드시 `-foreground` 계열 토큰을 써야 한다.
    name: "배경 토큰을 글자색으로 사용",
    pattern: /\btext-(?:muted|card|popover|background|border|input|secondary|accent)(?!-foreground)\b/g,
  },
];

/**
 * 허용 예외 — 모달 스크림은 두 모드 모두 검은 반투명이 의도된 값이며,
 * 레포 공통 `components/ui/dialog.tsx`(`bg-black/80`)와 동일한 관례다.
 * 불투명도 수식어(`/40`)가 붙은 형태만 허용한다.
 */
const ALLOWED = [/\bbg-black\/\d{1,3}\b/g];

function collectSourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      return entry.name === "__tests__" ? [] : collectSourceFiles(fullPath);
    }
    return /\.tsx?$/.test(entry.name) ? [fullPath] : [];
  });
}

const SOURCE_FILES = collectSourceFiles(ADMIN_ROOT);

function stripAllowed(source: string): string {
  return ALLOWED.reduce((acc, pattern) => acc.replace(pattern, ""), source);
}

describe("어드민 콘솔 전체가 시맨틱 토큰만 사용한다", () => {
  it("검사 대상 파일을 실제로 수집한다", () => {
    expect(SOURCE_FILES.length).toBeGreaterThan(10);
  });

  it.each(SOURCE_FILES.map((file) => ({ file, label: relative(ADMIN_ROOT, file) })))(
    "$label 에 하드코딩 색이 없다",
    ({ file }) => {
      const source = stripAllowed(readFileSync(file, "utf-8"));

      const violations = RULES.flatMap((rule) =>
        (source.match(rule.pattern) ?? []).map((match) => `${rule.name}: ${match}`)
      );

      expect(violations).toEqual([]);
    }
  );
});
