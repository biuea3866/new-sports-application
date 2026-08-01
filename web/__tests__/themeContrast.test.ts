/**
 * 테마 토큰 색 대비(WCAG) 가드.
 *
 * 배경: 어드민 다크 모드 이관에서 `text-red-600` 등 17개 라인을 `text-destructive`로 라우팅했는데,
 * 다크 `--destructive`(0 62.8% 30.6%)는 `bg-card` 위 대비가 **2.00:1**이라
 * 09-MCP-사용분석의 "에러 수" 등 오류 텍스트가 사실상 보이지 않았다.
 * 카드는 올바르게 어두워졌는데 오류 경로만 역행한 회귀였다.
 *
 * 이 테스트는 `app/globals.css`의 토큰 값을 직접 파싱해 대비를 계산하므로,
 * 누가 토큰을 조정해도 판독 가능성이 깨지면 즉시 실패한다.
 *
 * 임계값 근거(WCAG 2.1 AA)
 * - 본문 텍스트: 4.5:1
 * - UI 컴포넌트·그래픽 오브젝트(실선 배경 버튼/배지의 색 구분): 3:1
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { join } from "path";

type Hsl = readonly [number, number, number];
type Rgb = readonly [number, number, number];

const GLOBALS_CSS = readFileSync(join(__dirname, "..", "app", "globals.css"), "utf-8");

/** `.dark { ... }` / `:root { ... }` 블록에서 `--token: H S% L%` 를 읽는다. */
function readToken(scope: ":root" | ".dark", token: string): Hsl {
  const scopePattern = new RegExp(`${scope.replace(".", "\\.")}\\s*\\{([\\s\\S]*?)\\n\\s*\\}`, "m");
  const scopeBody = GLOBALS_CSS.match(scopePattern)?.[1];
  if (scopeBody === undefined) throw new Error(`${scope} 블록을 찾지 못했습니다.`);

  const valuePattern = new RegExp(`--${token}:\\s*([\\d.]+)\\s+([\\d.]+)%\\s+([\\d.]+)%`);
  const matched = scopeBody.match(valuePattern);
  if (matched === null) throw new Error(`${scope} 에서 --${token} 을 찾지 못했습니다.`);

  return [Number(matched[1]), Number(matched[2]), Number(matched[3])];
}

function hslToRgb([hue, saturation, lightness]: Hsl): Rgb {
  const s = saturation / 100;
  const l = lightness / 100;
  const k = (n: number): number => (n + hue / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number): number => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return [f(0), f(8), f(4)];
}

function relativeLuminance(rgb: Rgb): number {
  const [r, g, b] = rgb.map((channel) =>
    channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)
  ) as unknown as Rgb;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(foreground: Rgb, background: Rgb): number {
  const first = relativeLuminance(foreground);
  const second = relativeLuminance(background);
  const [lighter, darker] = first > second ? [first, second] : [second, first];
  return (lighter + 0.05) / (darker + 0.05);
}

/** 반투명 틴트(`bg-destructive/15` 등)를 불투명 배경 위에 합성한다. */
function composite(foreground: Rgb, background: Rgb, alpha: number): Rgb {
  return foreground.map((channel, index) => channel * alpha + background[index]! * (1 - alpha)) as
    unknown as Rgb;
}

const TEXT_MINIMUM = 4.5;
const UI_COMPONENT_MINIMUM = 3;

/** 카드 위에 올라가는 본문 색 토큰 — 전부 본문 기준을 넘겨야 한다. */
const TEXT_TOKENS = ["destructive", "success", "warning", "muted-foreground", "foreground"] as const;

describe("다크 모드 텍스트 토큰이 카드 위에서 판독 가능하다", () => {
  const darkCard = hslToRgb(readToken(".dark", "card"));

  it.each(TEXT_TOKENS)("text-%s 가 bg-card 위에서 AA 본문 기준을 만족한다", (token) => {
    const ratio = contrastRatio(hslToRgb(readToken(".dark", token)), darkCard);

    expect(ratio).toBeGreaterThanOrEqual(TEXT_MINIMUM);
  });

  // 배지·알림 박스는 같은 색의 반투명 틴트를 배경으로 깐다 (`bg-destructive/15 text-destructive`).
  it("text-destructive 가 bg-destructive/15 틴트 위에서도 본문 기준을 만족한다", () => {
    const destructive = hslToRgb(readToken(".dark", "destructive"));
    const tinted = composite(destructive, darkCard, 0.15);

    expect(contrastRatio(destructive, tinted)).toBeGreaterThanOrEqual(TEXT_MINIMUM);
  });
});

/**
 * 라이트 모드 — 래칫(악화 차단) + 부채 명시.
 *
 * 아래 실측값은 **이 브랜치 이전부터 존재하던 토큰 값**이며 일부는 AA 미달이다
 * (`StatusBadge`·`GlobalToggleField` 등 기존 화면이 이미 이 조합을 쓰고 있었다).
 * 라이트 토큰을 조정하면 `/portal` 전반 외관이 바뀌므로 이번 범위에서 값은 건드리지 않고,
 * **현재 수준 아래로 떨어지는 것만 차단**한다. AA 미달 해소는 별도 티켓 대상이다.
 */
const LIGHT_MODE_BASELINE: ReadonlyArray<{ token: string; minimum: number; meetsAa: boolean }> = [
  { token: "foreground", minimum: 19, meetsAa: true },
  { token: "muted-foreground", minimum: 4.7, meetsAa: true },
  { token: "destructive", minimum: 3.7, meetsAa: false },
  { token: "success", minimum: 2.25, meetsAa: false },
  { token: "warning", minimum: 2.1, meetsAa: false },
];

describe("라이트 모드 텍스트 토큰 대비가 현재 수준 아래로 떨어지지 않는다", () => {
  const lightCard = hslToRgb(readToken(":root", "card"));

  it.each(LIGHT_MODE_BASELINE)("text-$token 이 기준선($minimum:1) 이상을 유지한다", ({ token, minimum }) => {
    const ratio = contrastRatio(hslToRgb(readToken(":root", token)), lightCard);

    expect(ratio).toBeGreaterThanOrEqual(minimum);
  });

  it("AA 미달로 남아 있는 라이트 토큰이 무엇인지 기록한다 (사전 결함 — 별도 티켓)", () => {
    const belowAa = LIGHT_MODE_BASELINE.filter(({ meetsAa }) => !meetsAa).map(({ token }) => token);

    expect(belowAa).toEqual(["destructive", "success", "warning"]);
  });
});

describe("실선 배경 조합이 UI 컴포넌트 기준을 만족한다", () => {
  // `components/ui/{button,badge,toast}.tsx` 와 TokenList 확인 버튼이 쓰는 조합.
  it.each([":root", ".dark"] as const)(
    "%s 의 bg-destructive + text-destructive-foreground",
    (scope) => {
      const background = hslToRgb(readToken(scope, "destructive"));
      const foreground = hslToRgb(readToken(scope, "destructive-foreground"));

      expect(contrastRatio(foreground, background)).toBeGreaterThanOrEqual(UI_COMPONENT_MINIMUM);
    }
  );
});
