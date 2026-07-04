/**
 * ThemedText — textPrimary 토큰 색으로 렌더됩니다 (라이트/다크 각각).
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ThemedText } from '../ThemedText';
import { lightTokens, darkTokens } from '../../../theme/tokens';

describe('ThemedText', () => {
  it('라이트 스킴에서 textPrimary 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<ThemedText>안내 문구</ThemedText>);

    expect(screen.getByText('안내 문구')).toHaveStyle({ color: lightTokens.textPrimary });
  });

  it('다크 스킴에서 textPrimary 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<ThemedText>안내 문구</ThemedText>);

    expect(screen.getByText('안내 문구')).toHaveStyle({ color: darkTokens.textPrimary });
  });

  it('variant="danger"로 지정하면 danger 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<ThemedText variant="danger">오류 문구</ThemedText>);

    expect(screen.getByText('오류 문구')).toHaveStyle({ color: lightTokens.danger });
  });
});
