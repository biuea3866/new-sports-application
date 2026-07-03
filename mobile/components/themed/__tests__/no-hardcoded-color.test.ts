/**
 * 테마 프리미티브 컴포넌트에는 색 하드코딩(hex)이 없어야 합니다.
 * 색은 반드시 theme/tokens.ts 를 경유해야 합니다 (theme/tokens.ts 정의부 자체는 예외).
 */
import fs from 'fs';
import path from 'path';

const THEMED_DIR = path.resolve(__dirname, '..');
const HEX_COLOR_PATTERN = /#[0-9A-Fa-f]{3,8}\b/;

function listThemedSourceFiles(): string[] {
  return fs
    .readdirSync(THEMED_DIR)
    .filter((fileName) => fileName.endsWith('.tsx') || fileName.endsWith('.ts'))
    .map((fileName) => path.join(THEMED_DIR, fileName));
}

describe('테마 프리미티브 색 하드코딩 금지', () => {
  it('components/themed/*.tsx 소스에 하드코딩 hex 색상이 없다', () => {
    const filesWithHardcodedColor = listThemedSourceFiles()
      .map((filePath) => ({ filePath, content: fs.readFileSync(filePath, 'utf-8') }))
      .filter(({ content }) => HEX_COLOR_PATTERN.test(content))
      .map(({ filePath }) => filePath);

    expect(filesWithHardcodedColor).toEqual([]);
  });
});
