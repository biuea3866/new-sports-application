/**
 * createStyles — 화면별 StyleSheet를 테마 토큰 기반으로 생성하는 헬퍼 패턴.
 *
 * 사용 패턴:
 *   const styleFactory = createStyles((theme: ThemeTokens) =>
 *     StyleSheet.create({ container: { backgroundColor: theme.background } })
 *   );
 *   // 컴포넌트 내부
 *   const { tokens } = useTheme();
 *   const styles = styleFactory(tokens);
 *
 * 함수 자체는 타입 추론을 위한 항등 함수이며, 각 화면은 `theme`을 인자로 받는
 * 팩토리 함수를 정의해 하드코딩 색 없이 함수형 StyleSheet를 생성합니다.
 */
import type { ThemeTokens } from './tokens';

export function createStyles<T>(factory: (theme: ThemeTokens) => T): (theme: ThemeTokens) => T {
  return factory;
}
