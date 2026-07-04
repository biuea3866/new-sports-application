/**
 * theme/tokens.ts — 시맨틱 테마 토큰(라이트/다크) 값 검증
 * 근거: 20260704-채팅시스템고도화-design-fe-app.md "테마 토큰 정의 표"
 */
import { lightTokens, darkTokens } from '../tokens';

describe('테마 토큰 라이트/다크 값', () => {
  it('라이트와 다크 토큰 객체가 동일한 키 집합을 갖는다', () => {
    const lightKeys = Object.keys(lightTokens).sort();
    const darkKeys = Object.keys(darkTokens).sort();

    expect(darkKeys).toEqual(lightKeys);
  });

  it('라이트 토큰 값이 설계 문서 테마 토큰 정의 표와 일치한다', () => {
    expect(lightTokens.background).toBe('#FFFFFF');
    expect(lightTokens.surface).toBe('#F9FAFB');
    expect(lightTokens.surfaceElevated).toBe('#FFFFFF');
    expect(lightTokens.textPrimary).toBe('#191F28');
    expect(lightTokens.textSecondary).toBe('#4E5968');
    expect(lightTokens.textTertiary).toBe('#8B95A1');
    expect(lightTokens.border).toBe('#E5E8EB');
    expect(lightTokens.accent).toBe('#3182F6');
    expect(lightTokens.accentText).toBe('#FFFFFF');
    expect(lightTokens.bubbleMine).toBe('#3182F6');
    expect(lightTokens.bubbleMineText).toBe('#FFFFFF');
    expect(lightTokens.bubbleOther).toBe('#F2F4F6');
    expect(lightTokens.bubbleOtherText).toBe('#191F28');
    expect(lightTokens.badge).toBe('#F04452');
    expect(lightTokens.badgeText).toBe('#FFFFFF');
    expect(lightTokens.success).toBe('#12B886');
    expect(lightTokens.danger).toBe('#F04452');
    expect(lightTokens.overlay).toBe('rgba(0,0,0,0.4)');
    expect(lightTokens.typing).toBe('#8B95A1');
  });

  it('다크 토큰 값이 설계 문서 테마 토큰 정의 표와 일치한다', () => {
    expect(darkTokens.background).toBe('#17171C');
    expect(darkTokens.surface).toBe('#202027');
    expect(darkTokens.surfaceElevated).toBe('#26262E');
    expect(darkTokens.textPrimary).toBe('#F2F4F6');
    expect(darkTokens.textSecondary).toBe('#B0B8C1');
    expect(darkTokens.textTertiary).toBe('#6B7684');
    expect(darkTokens.border).toBe('#2E2E36');
    expect(darkTokens.accent).toBe('#4E93FB');
    expect(darkTokens.accentText).toBe('#FFFFFF');
    expect(darkTokens.bubbleMine).toBe('#3B5BDB');
    expect(darkTokens.bubbleMineText).toBe('#F2F4F6');
    expect(darkTokens.bubbleOther).toBe('#2E2E36');
    expect(darkTokens.bubbleOtherText).toBe('#F2F4F6');
    expect(darkTokens.badge).toBe('#F76A78');
    expect(darkTokens.badgeText).toBe('#FFFFFF');
    expect(darkTokens.success).toBe('#2AC29B');
    expect(darkTokens.danger).toBe('#F76A78');
    expect(darkTokens.overlay).toBe('rgba(0,0,0,0.6)');
    expect(darkTokens.typing).toBe('#6B7684');
  });
});
