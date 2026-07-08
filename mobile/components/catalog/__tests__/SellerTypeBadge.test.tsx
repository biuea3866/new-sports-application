/**
 * SellerTypeBadge — PRODUCT 항목의 판매자 유형 배지. B2B는 "브랜드"(accent, 화면 유일 강조),
 * B2C는 "중고"(중립)로 렌더하고, null이면 아무것도 렌더하지 않는다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { SellerTypeBadge } from '../SellerTypeBadge';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';
import { lightTokens, darkTokens } from '../../../theme/tokens';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;

describe('SellerTypeBadge', () => {
  beforeEach(() => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
  });

  it('B2B면 브랜드 라벨을 accent 배지로 렌더한다', () => {
    render(<SellerTypeBadge sellerType="B2B" />);

    expect(screen.getByText('브랜드')).toBeTruthy();
    expect(screen.getByTestId('seller-type-badge')).toHaveStyle({
      backgroundColor: lightTokens.accent,
    });
  });

  it('B2C면 중고 라벨을 중립 배지로 렌더한다', () => {
    render(<SellerTypeBadge sellerType="B2C" />);

    expect(screen.getByText('중고')).toBeTruthy();
    expect(screen.getByTestId('seller-type-badge')).toHaveStyle({
      backgroundColor: lightTokens.surface,
    });
  });

  it('null이면 아무것도 렌더하지 않는다', () => {
    const { toJSON } = render(<SellerTypeBadge sellerType={null} />);

    expect(toJSON()).toBeNull();
  });

  it('다크 모드에서 B2B 배지가 다크 accent 토큰으로 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

    render(<SellerTypeBadge sellerType="B2B" />);

    expect(screen.getByTestId('seller-type-badge')).toHaveStyle({
      backgroundColor: darkTokens.accent,
    });
  });

  it('다크 모드에서 B2C 배지가 다크 surface 토큰으로 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

    render(<SellerTypeBadge sellerType="B2C" />);

    expect(screen.getByTestId('seller-type-badge')).toHaveStyle({
      backgroundColor: darkTokens.surface,
    });
  });
});
