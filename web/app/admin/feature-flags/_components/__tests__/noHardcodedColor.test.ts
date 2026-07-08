import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { join } from "path";

const COMPONENT_FILES = [
  "StatusBadge.tsx",
  "TypeBadge.tsx",
  "ChangeTypeBadge.tsx",
  "StrategySummary.tsx",
];

const HARDCODED_COLOR_PATTERN = /#[0-9a-fA-F]{3,8}\b|(?:bg|text|border)-(?:gray|green|red|blue|yellow)-\d{2,3}/;

describe("배지·전략 요약 컴포넌트에 하드코딩 색이 없다", () => {
  it.each(COMPONENT_FILES)("%s에 하드코딩 색이 없다", (fileName) => {
    const source = readFileSync(join(__dirname, "..", fileName), "utf-8");

    expect(source).not.toMatch(HARDCODED_COLOR_PATTERN);
  });
});
