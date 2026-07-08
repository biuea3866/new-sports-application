/**
 * DropStatusBadge — status별 라벨/톤(SCHEDULED/OPEN/SOLD_OUT/CLOSED)을 표시합니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { DropStatusBadge } from '../DropStatusBadge';

describe('DropStatusBadge', () => {
  it.each([
    ['SCHEDULED', '오픈예정'],
    ['OPEN', '판매중'],
    ['SOLD_OUT', '재고소진'],
    ['CLOSED', '판매종료'],
  ] as const)('status=%s이면 "%s" 라벨을 표시한다', (status, label) => {
    mockUseColorScheme.mockReturnValue('light');

    render(<DropStatusBadge status={status} />);

    expect(screen.getByText(label)).toBeTruthy();
  });

  it('다크 모드에서도 라벨이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<DropStatusBadge status="OPEN" />);

    expect(screen.getByText('판매중')).toBeTruthy();
  });
});
