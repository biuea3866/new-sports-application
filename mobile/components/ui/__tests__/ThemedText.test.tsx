/**
 * components/ui/ThemedText — theme/ThemedText 재노출. 텍스트가 지정된 variant 토큰 색으로 렌더된다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ThemedText } from '../ThemedText';
import { lightTokens } from '../../../theme/tokens';

describe('ThemedText (ui)', () => {
  it('기본(primary) variant는 textPrimary 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<ThemedText>안녕하세요</ThemedText>);

    expect(screen.getByText('안녕하세요')).toHaveStyle({ color: lightTokens.textPrimary });
  });

  it('secondary variant 지정 시 textSecondary 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<ThemedText variant="secondary">보조 텍스트</ThemedText>);

    expect(screen.getByText('보조 텍스트')).toHaveStyle({ color: lightTokens.textSecondary });
  });
});
