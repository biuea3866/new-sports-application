/**
 * ProgramCard — 이름·가격·소요시간·정원을 표시하고 탭 시 onPress를 호출한다
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';

import type { ProgramResponse } from '../../../api/program';
import { ProgramCard } from '../ProgramCard';

const program: ProgramResponse = {
  id: 1,
  facilityId: 'facility-1',
  ownerUserId: 42,
  name: 'PT 1:1',
  description: null,
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

describe('ProgramCard', () => {
  it('이름·가격·소요시간·정원을 표시한다', () => {
    render(<ProgramCard program={program} onPress={jest.fn()} />);

    expect(screen.getByText('PT 1:1')).toBeTruthy();
    expect(screen.getByText('50,000원')).toBeTruthy();
    expect(screen.getByText('· 60분')).toBeTruthy();
    expect(screen.getByText('정원 1명')).toBeTruthy();
  });

  it('카드를 탭하면 onPress가 호출된다', () => {
    const onPress = jest.fn();
    render(<ProgramCard program={program} onPress={onPress} />);

    fireEvent.press(screen.getByLabelText(/PT 1:1, 50,000원, 60분, 정원 1명, 예약하기/));

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('정원이 다른 프로그램도 올바른 라벨로 렌더된다', () => {
    const groupProgram: ProgramResponse = {
      ...program,
      id: 2,
      name: '필라테스 그룹',
      price: 30000,
      capacity: 6,
      durationMinutes: 50,
    };

    render(<ProgramCard program={groupProgram} onPress={jest.fn()} />);

    expect(screen.getByText('필라테스 그룹')).toBeTruthy();
    expect(screen.getByText('30,000원')).toBeTruthy();
    expect(screen.getByText('정원 6명')).toBeTruthy();
  });
});
