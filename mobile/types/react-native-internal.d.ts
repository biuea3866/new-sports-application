/**
 * react-native의 딥 임포트 경로 중 테스트에서 mock 제어점으로 사용하는 모듈의
 * 타입 선언입니다. react-native 패키지의 공개 타입(exports 필드)에는
 * 포함되지 않아 별도로 선언합니다.
 *
 * 용도: `react-native/jest/setup.js`가 이 경로를 `jest.fn(() => 'light')`로
 * 전역 mock 처리하므로, 테스트에서 `mockReturnValue`로 스킴을 제어할 때 사용합니다.
 */
declare module 'react-native/Libraries/Utilities/useColorScheme' {
  import type { ColorSchemeName } from 'react-native';

  const useColorScheme: jest.Mock<ColorSchemeName, []>;
  export default useColorScheme;
}
