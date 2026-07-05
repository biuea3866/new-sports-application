/**
 * components/ui/ThemedView — theme/ThemedView 재노출. 다크 모드에서 배경이 다크 토큰 값으로 렌더된다.
 */
import React from 'react';
import { Text } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ThemedView } from '../ThemedView';
import { darkTokens, lightTokens } from '../../../theme/tokens';

describe('ThemedView (ui)', () => {
  it('다크 모드에서 배경이 다크 토큰 background 값으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(
      <ThemedView testID="ui-themed-view">
        <Text>내용</Text>
      </ThemedView>
    );

    expect(screen.getByTestId('ui-themed-view')).toHaveStyle({
      backgroundColor: darkTokens.background,
    });
  });

  it('라이트 모드 + surface 배경 지정 시 라이트 surface 토큰 값으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(
      <ThemedView testID="ui-themed-view" background="surface">
        <Text>내용</Text>
      </ThemedView>
    );

    expect(screen.getByTestId('ui-themed-view')).toHaveStyle({
      backgroundColor: lightTokens.surface,
    });
  });
});
