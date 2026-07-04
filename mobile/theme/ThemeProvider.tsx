/**
 * ThemeProvider — 앱 루트 마운트용 프로바이더 정의.
 *
 * 테마 상태(useThemeStore)는 Zustand로 Context 없이도 전역 접근 가능하므로
 * 이 컴포넌트는 현재 children을 그대로 렌더링합니다. 앱 루트(`_layout.tsx`)에
 * 실제로 마운트하는 작업은 FE-15(통합 티켓)에서 처리하며, 이 티켓은 정의만 제공합니다.
 */
import type { PropsWithChildren, ReactElement } from 'react';

export function ThemeProvider({ children }: PropsWithChildren): ReactElement {
  return <>{children}</>;
}
