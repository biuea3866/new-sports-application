/**
 * createStyles — 화면별 StyleSheet를 테마 토큰 기반으로 생성하는 헬퍼 패턴 검증.
 * 사용 패턴: `const styleFactory = createStyles((theme) => StyleSheet.create({...}))`
 */
import { StyleSheet } from 'react-native';
import { createStyles } from '../createStyles';
import { lightTokens, darkTokens } from '../tokens';

describe('createStyles', () => {
  it('전달한 테마 토큰으로 스타일을 생성한다(라이트)', () => {
    const styleFactory = createStyles((theme) =>
      StyleSheet.create({
        container: { backgroundColor: theme.background },
        title: { color: theme.textPrimary },
      })
    );

    const styles = styleFactory(lightTokens);

    expect(styles.container.backgroundColor).toBe(lightTokens.background);
    expect(styles.title.color).toBe(lightTokens.textPrimary);
  });

  it('다른 테마 토큰을 넣으면 다른 스타일 값을 생성한다(다크)', () => {
    const styleFactory = createStyles((theme) =>
      StyleSheet.create({
        container: { backgroundColor: theme.background },
      })
    );

    const styles = styleFactory(darkTokens);

    expect(styles.container.backgroundColor).toBe(darkTokens.background);
  });
});
