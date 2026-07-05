/**
 * components/ui 프리미티브에는 색 하드코딩(hex)이 없어야 합니다.
 * 색은 반드시 theme/tokens.ts 를 경유해야 합니다.
 */
import fs from 'fs';
import path from 'path';

const UI_DIR = path.resolve(__dirname, '..');
const HEX_COLOR_PATTERN = /#[0-9A-Fa-f]{3,8}\b/;

function listUiSourceFiles(): string[] {
  return fs
    .readdirSync(UI_DIR)
    .filter((fileName) => fileName.endsWith('.tsx') || fileName.endsWith('.ts'))
    .map((fileName) => path.join(UI_DIR, fileName));
}

describe('UI 프리미티브 색 하드코딩 금지', () => {
  it('components/ui/*.tsx 소스에 하드코딩 hex 색상이 없다', () => {
    const filesWithHardcodedColor = listUiSourceFiles()
      .map((filePath) => ({ filePath, content: fs.readFileSync(filePath, 'utf-8') }))
      .filter(({ content }) => HEX_COLOR_PATTERN.test(content))
      .map(({ filePath }) => filePath);

    expect(filesWithHardcodedColor).toEqual([]);
  });
});
