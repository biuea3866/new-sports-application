/**
 * PartialFailureBanner — labels가 있을 때만 안내 문구와 실패 유형 라벨을 alert role로 노출한다.
 * 색은 useTheme() 토큰을 경유하는지 라이트/다크 두 모드에서 검증한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { PartialFailureBanner } from '../PartialFailureBanner';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';
import { lightTokens, darkTokens } from '../../../theme/tokens';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;

describe('PartialFailureBanner', () => {
  beforeEach(() => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
  });

  it('labels가 있으면 안내 문구와 실패 유형 라벨을 렌더한다', () => {
    render(<PartialFailureBanner labels={['티켓', '예약']} />);

    expect(screen.getByText('일부 결과를 불러오지 못했어요')).toBeTruthy();
    expect(screen.getByText('티켓, 예약')).toBeTruthy();
  });

  it('labels가 빈 배열이면 아무것도 렌더하지 않는다', () => {
    const { toJSON } = render(<PartialFailureBanner labels={[]} />);

    expect(toJSON()).toBeNull();
  });

  it('배너 문구가 alert role로 노출된다', () => {
    render(<PartialFailureBanner labels={['상품']} />);

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('라이트 모드에서 surfaceElevated 토큰으로 배경이 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });

    render(<PartialFailureBanner labels={['상품']} />);

    expect(screen.getByTestId('partial-failure-banner')).toHaveStyle({
      backgroundColor: lightTokens.surfaceElevated,
      borderColor: lightTokens.border,
    });
  });

  it('다크 모드에서 surfaceElevated 토큰으로 배경이 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

    render(<PartialFailureBanner labels={['상품']} />);

    expect(screen.getByTestId('partial-failure-banner')).toHaveStyle({
      backgroundColor: darkTokens.surfaceElevated,
      borderColor: darkTokens.border,
    });
  });
});
