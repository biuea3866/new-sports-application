/**
 * RemainingStockBar — 진행 바가 가용 폭을 채우는지 검증한다.
 *
 * 회귀 배경: "남은 수량 300/300"인데 진행 바가 화면 폭의 30% 정도만 차지해 재고가 거의 없는
 * 것처럼 보였다(유즈케이스 캡쳐 14-한정판-드롭). 비율 계산은 정상(=100%)이었고, 바를 감싼
 * 컨테이너가 부모의 alignItems: 'flex-start' 때문에 라벨 텍스트 폭으로 줄어든 것이 원인이다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { RemainingStockBar } from '../RemainingStockBar';

function flattenStyle(style: unknown): Record<string, unknown> {
  if (Array.isArray(style)) {
    return style.reduce<Record<string, unknown>>(
      (merged, entry) => ({ ...merged, ...flattenStyle(entry) }),
      {}
    );
  }
  return (style ?? {}) as Record<string, unknown>;
}

describe('RemainingStockBar 레이아웃', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('바 컨테이너가 부모 정렬과 무관하게 가용 폭을 채운다', () => {
    render(<RemainingStockBar remaining={300} limited={300} />);

    const container = screen.getByLabelText('남은 수량 300개 중 300개');

    expect(flattenStyle(container.props.style).alignSelf).toBe('stretch');
  });

  it('재고가 가득 차면 채움 폭이 100%다', () => {
    render(<RemainingStockBar remaining={300} limited={300} />);

    const fill = screen.getByTestId('remaining-stock-fill');

    expect(flattenStyle(fill.props.style).width).toBe('100%');
  });

  it('재고가 절반이면 채움 폭이 50%다', () => {
    render(<RemainingStockBar remaining={50} limited={100} />);

    const fill = screen.getByTestId('remaining-stock-fill');

    expect(flattenStyle(fill.props.style).width).toBe('50%');
  });

  it('한정 수량이 0이면 채움 폭이 0%다', () => {
    render(<RemainingStockBar remaining={0} limited={0} />);

    const fill = screen.getByTestId('remaining-stock-fill');

    expect(flattenStyle(fill.props.style).width).toBe('0%');
  });
});
