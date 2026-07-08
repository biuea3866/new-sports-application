/**
 * ThemeProvider — 앱 루트 마운트용 프로바이더 정의(현재는 children pass-through).
 * 마운트 와이어업(_layout.tsx)은 FE-15 소관이며, 이 테스트는 정의부만 검증합니다.
 */
import React from 'react';
import { Text } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import { ThemeProvider } from '../ThemeProvider';

describe('ThemeProvider', () => {
  it('children을 그대로 렌더링한다', () => {
    render(
      <ThemeProvider>
        <Text>테마 하위 화면</Text>
      </ThemeProvider>
    );

    expect(screen.getByText('테마 하위 화면')).toBeTruthy();
  });
});
