/**
 * [버그1] 다크 모드 흰 배경 + 흰 글자 회귀 방지 — 화면(app/**)·공용 컴포넌트(components/**)
 * 소스에 색 하드코딩(hex)이 없어야 합니다. 색은 반드시 theme/tokens.ts(useTheme())를
 * 경유해야 라이트/다크 두 모드가 함께 반영됩니다.
 *
 * components/ui·components/themed는 이미 no-hardcoded-color.test.ts로 개별 검증하지만,
 * 화면 루트(app/**)에서 반복 발생한 회귀(예: 홈 화면 카드 배경 미설정)를 잡기 위해
 * 프로젝트 전체 화면·컴포넌트 범위로 넓혀 검증합니다.
 *
 * theme/tokens.ts 는 토큰 정의 자체이므로 예외입니다(private-allow:no-hardcoded-color 주석 존재).
 */
import fs from 'fs';
import path from 'path';

const ROOT_DIR = path.resolve(__dirname, '../..');
const SCAN_DIRS = ['app', 'components'];
const HEX_COLOR_PATTERN = /#[0-9A-Fa-f]{3,8}\b/;
const ALLOW_COMMENT = 'private-allow:no-hardcoded-color';

function listSourceFiles(dir: string): string[] {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === '__tests__' || entry.name === 'node_modules') {
        return [];
      }
      return listSourceFiles(fullPath);
    }
    if (
      (entry.name.endsWith('.tsx') || entry.name.endsWith('.ts')) &&
      !entry.name.endsWith('.test.ts') &&
      !entry.name.endsWith('.test.tsx')
    ) {
      return [fullPath];
    }
    return [];
  });
}

function hasDisallowedHardcodedColor(content: string): boolean {
  return content
    .split('\n')
    .some((line) => HEX_COLOR_PATTERN.test(line) && !line.includes(ALLOW_COMMENT));
}

describe('화면·공용 컴포넌트 색 하드코딩 금지 (다크모드 회귀 방지)', () => {
  it('app/**, components/** 소스에 하드코딩 hex 색상이 없다', () => {
    const filesWithHardcodedColor = SCAN_DIRS.flatMap((dir) =>
      listSourceFiles(path.join(ROOT_DIR, dir))
    )
      .map((filePath) => ({ filePath, content: fs.readFileSync(filePath, 'utf-8') }))
      .filter(({ content }) => hasDisallowedHardcodedColor(content))
      .map(({ filePath }) => path.relative(ROOT_DIR, filePath));

    expect(filesWithHardcodedColor).toEqual([]);
  });
});
