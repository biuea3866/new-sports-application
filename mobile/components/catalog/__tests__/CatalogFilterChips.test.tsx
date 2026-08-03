/**
 * CatalogFilterChips — 통합 검색 화면의 가로 스크롤 필터 칩.
 *
 * 회귀 배경: 필터 칩 줄이 붕괴해 "한정판"이 한정/판, "클래스"가 클래/스로 2줄 분리됐고,
 * 선택된 "전체" 칩은 배경이 텍스트보다 좁아 글자가 배경 밖으로 삐져나왔다
 * (유즈케이스 캡쳐 11-통합-카탈로그). 폭이 정해지지 않은 가로 스크롤 안에서 균등분할(flex)
 * 세그먼트를 쓴 것이 원인이라, 칩은 내용 크기로 잡고 라벨은 한 줄로 고정한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { CatalogFilterChips, type CatalogFilterChipsProps } from '../CatalogFilterChips';
import { darkTokens, lightTokens } from '../../../theme/tokens';

const OPTIONS = [
  { label: '전체', value: 'ALL' },
  { label: '상품', value: 'PRODUCT' },
  { label: '한정판', value: 'LIMITED_DROP' },
  { label: '티켓', value: 'TICKET' },
  { label: '클래스', value: 'PROGRAM' },
  { label: '모집', value: 'RECRUITMENT' },
];

function buildProps(overrides: Partial<CatalogFilterChipsProps> = {}): CatalogFilterChipsProps {
  return {
    options: OPTIONS,
    value: 'ALL',
    onChange: jest.fn(),
    accessibilityLabel: '상품 유형 필터',
    ...overrides,
  };
}

describe('CatalogFilterChips', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('모든 필터 칩을 렌더한다', () => {
    render(<CatalogFilterChips {...buildProps()} />);

    OPTIONS.forEach((option) => {
      expect(screen.getByLabelText(option.label)).toBeTruthy();
    });
  });

  it('두 글자를 넘는 라벨도 한 줄로 유지해 줄바꿈으로 쪼개지지 않는다', () => {
    render(<CatalogFilterChips {...buildProps()} />);

    ['한정판', '클래스'].forEach((label) => {
      expect(screen.getByText(label).props.numberOfLines).toBe(1);
    });
  });

  it('선택된 칩만 selected 상태로 표시한다', () => {
    render(<CatalogFilterChips {...buildProps({ value: 'LIMITED_DROP' })} />);

    expect(screen.getByLabelText('한정판').props.accessibilityState.selected).toBe(true);
    expect(screen.getByLabelText('전체').props.accessibilityState.selected).toBe(false);
  });

  it('칩을 누르면 해당 값으로 onChange를 호출한다', () => {
    const onChange = jest.fn();
    render(<CatalogFilterChips {...buildProps({ onChange })} />);

    fireEvent.press(screen.getByLabelText('클래스'));

    expect(onChange).toHaveBeenCalledWith('PROGRAM');
  });

  it('선택 칩 배경은 라이트·다크 모두 테마 토큰을 쓴다', () => {
    const { rerender } = render(<CatalogFilterChips {...buildProps({ value: 'TICKET' })} />);
    const lightChipStyle = screen.getByLabelText('티켓').props.style;
    expect(JSON.stringify(lightChipStyle)).toContain(lightTokens.accent);

    mockUseColorScheme.mockReturnValue('dark');
    rerender(<CatalogFilterChips {...buildProps({ value: 'TICKET' })} />);
    const darkChipStyle = screen.getByLabelText('티켓').props.style;
    expect(JSON.stringify(darkChipStyle)).toContain(darkTokens.accent);
  });
});
