/**
 * CatalogSearchControls — 검색 입력(300ms 디바운스) + itemType/sellerType 세그먼트 필터.
 * 근거: FE-08 티켓, design-fe-app.md 와이어프레임 ①·컴포넌트 트리.
 * 색은 useTheme() 토큰을 경유하는지 라이트/다크 두 모드에서 검증한다.
 */
import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { CatalogSearchControls, type CatalogSearchControlsProps } from '../CatalogSearchControls';
import { lightTokens, darkTokens } from '../../../theme/tokens';

function buildProps(
  overrides: Partial<CatalogSearchControlsProps> = {}
): CatalogSearchControlsProps {
  return {
    keyword: '',
    onKeywordChange: jest.fn(),
    itemType: undefined,
    onItemTypeChange: jest.fn(),
    sellerType: undefined,
    onSellerTypeChange: jest.fn(),
    ...overrides,
  };
}

describe('CatalogSearchControls', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('검색 입력 타이핑이 디바운스 후 onKeywordChange를 호출한다', () => {
    const props = buildProps();

    render(<CatalogSearchControls {...props} />);
    fireEvent.changeText(screen.getByLabelText('검색어 입력'), '요가');

    expect(props.onKeywordChange).not.toHaveBeenCalled();

    act(() => {
      jest.advanceTimersByTime(300);
    });

    expect(props.onKeywordChange).toHaveBeenCalledWith('요가');
  });

  it('itemType 세그먼트 선택이 onItemTypeChange를 호출한다', () => {
    const props = buildProps();

    render(<CatalogSearchControls {...props} />);
    fireEvent.press(screen.getByRole('button', { name: '상품' }));

    expect(props.onItemTypeChange).toHaveBeenCalledWith('PRODUCT');
  });

  it('itemType이 PRODUCT일 때만 sellerType 세그먼트가 보인다', () => {
    const props = buildProps({ itemType: undefined });

    const { rerender } = render(<CatalogSearchControls {...props} />);
    expect(screen.queryByRole('button', { name: '중고' })).toBeNull();

    rerender(<CatalogSearchControls {...props} itemType="PRODUCT" />);
    expect(screen.getByRole('button', { name: '중고' })).toBeTruthy();
  });

  it('clear 버튼을 누르면 keyword가 빈 값으로 초기화된다', () => {
    const props = buildProps({ keyword: '요가' });

    render(<CatalogSearchControls {...props} />);
    fireEvent.press(screen.getByLabelText('검색어 지우기'));

    expect(props.onKeywordChange).toHaveBeenCalledWith('');
    expect(screen.getByLabelText('검색어 입력').props.value).toBe('');
  });

  it('라이트 모드에서 surfaceElevated 토큰으로 검색 입력 배경이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');
    const props = buildProps();

    render(<CatalogSearchControls {...props} />);

    expect(screen.getByTestId('catalog-search-input')).toHaveStyle({
      backgroundColor: lightTokens.surfaceElevated,
      borderColor: lightTokens.border,
    });
  });

  it('다크 모드에서 surfaceElevated 토큰으로 검색 입력 배경이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    const props = buildProps();

    render(<CatalogSearchControls {...props} />);

    expect(screen.getByTestId('catalog-search-input')).toHaveStyle({
      backgroundColor: darkTokens.surfaceElevated,
      borderColor: darkTokens.border,
    });
  });
});
