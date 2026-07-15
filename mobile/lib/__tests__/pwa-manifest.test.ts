/**
 * PWA 설치 요건 계약 테스트
 *
 * 웹 빌드가 "설치 가능한 PWA"로 성립하려면 manifest에 아래 필드가 있어야 하고,
 * HTML이 manifest를 링크하고 service worker를 등록해야 합니다.
 * Expo의 웹 export는 이 파일들을 만들어주지 않으므로 직접 관리합니다.
 * (public/은 export 시 dist 루트로 복사됩니다.)
 */
import { readFileSync } from 'fs';
import { join } from 'path';

const projectRoot = join(__dirname, '..', '..');

function readProjectFile(relativePath: string): string {
  return readFileSync(join(projectRoot, relativePath), 'utf-8');
}

describe('PWA manifest', () => {
  const manifest = JSON.parse(readProjectFile('public/manifest.json'));

  it('설치 프롬프트에 필요한 이름을 담는다', () => {
    expect(manifest.name).toBeTruthy();
    expect(manifest.short_name).toBeTruthy();
  });

  it('앱처럼 실행되도록 standalone 디스플레이를 선언한다', () => {
    expect(manifest.display).toBe('standalone');
  });

  it('루트에서 시작한다', () => {
    expect(manifest.start_url).toBe('/');
  });

  it('설치에 필요한 192px·512px 아이콘을 모두 제공한다', () => {
    const sizes = manifest.icons.map((icon: { sizes: string }) => icon.sizes);

    expect(sizes).toContain('192x192');
    expect(sizes).toContain('512x512');
  });

  it('테마 색과 배경 색을 선언한다', () => {
    expect(manifest.theme_color).toMatch(/^#[0-9a-fA-F]{6}$/);
    expect(manifest.background_color).toMatch(/^#[0-9a-fA-F]{6}$/);
  });
});

describe('PWA HTML 템플릿', () => {
  const html = readProjectFile('app/+html.tsx');

  it('manifest를 링크한다', () => {
    expect(html).toContain('rel="manifest"');
    expect(html).toContain('/manifest.json');
  });

  it('iOS 홈화면 설치를 위한 apple-touch-icon을 제공한다', () => {
    expect(html).toContain('apple-touch-icon');
  });

  it('service worker를 등록한다', () => {
    expect(html).toContain('serviceWorker');
    expect(html).toContain('/sw.js');
  });
});

describe('service worker', () => {
  const serviceWorker = readProjectFile('public/sw.js');

  it('오프라인 대응을 위해 install 시 앱 셸을 캐시한다', () => {
    expect(serviceWorker).toContain("addEventListener('install'");
    expect(serviceWorker).toContain('caches.open');
  });

  it('fetch를 가로채 캐시로 응답할 수 있다', () => {
    expect(serviceWorker).toContain("addEventListener('fetch'");
  });

  it('activate 시 옛 버전 캐시를 정리한다', () => {
    expect(serviceWorker).toContain("addEventListener('activate'");
    expect(serviceWorker).toContain('caches.delete');
  });
});
