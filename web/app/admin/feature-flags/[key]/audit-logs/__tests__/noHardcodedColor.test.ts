import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { join } from "path";

const FILES = [
  { dir: "..", name: "page.tsx" },
  { dir: "../_components", name: "AuditLogTable.tsx" },
];

const HARDCODED_COLOR_PATTERN =
  /#[0-9a-fA-F]{3,8}\b|(?:bg|text|border)-(?:gray|green|red|blue|yellow)-\d{2,3}/;

describe("S5 변경 이력 화면에 하드코딩 색이 없다", () => {
  it.each(FILES)("$name에 하드코딩 색이 없다", ({ dir, name }) => {
    const source = readFileSync(join(__dirname, dir, name), "utf-8");

    expect(source).not.toMatch(HARDCODED_COLOR_PATTERN);
  });
});
