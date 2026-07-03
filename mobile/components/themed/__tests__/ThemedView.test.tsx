/**
 * ThemedView — background/surface 토큰 색으로 렌더됩니다.
 */
import React from 'react';
import { Text } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ThemedView } from '../ThemedView';
import { lightTokens, darkTokens } from '../../../theme/tokens';

describe('ThemedView', () => {
  it('기본값(background 토큰)으로 배경색이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(
      <ThemedView testID="themed-view">
        <Text>내용</Text>
      </ThemedView>
    );

    expect(screen.getByTestId('themed-view')).toHaveStyle({
      backgroundColor: lightTokens.background,
    });
  });

  it('background="surface" 지정 시 surface 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(
      <ThemedView testID="themed-view" background="surface">
        <Text>내용</Text>
      </ThemedView>
    );

    expect(screen.getByTestId('themed-view')).toHaveStyle({ backgroundColor: darkTokens.surface });
  });
});
