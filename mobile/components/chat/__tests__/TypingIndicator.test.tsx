/**
 * TypingIndicator — 상대 타이핑 이벤트 수신 시 표시되고 TTL(3초) 후 사라진다.
 * 근거: FE-10 티켓 테스트 케이스 "상대 타이핑 이벤트 수신 시 인디케이터가 표시되고 수 초 후 사라진다".
 */
import React from 'react';
import { act, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { TypingIndicator } from '../TypingIndicator';

describe('TypingIndicator', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('lastTypingAt이 null이면 아무것도 렌더하지 않는다', () => {
    render(<TypingIndicator lastTypingAt={null} />);

    expect(screen.queryByText('상대가 입력 중…')).toBeNull();
  });

  it('lastTypingAt이 주어지면 인디케이터가 표시된다', () => {
    render(<TypingIndicator lastTypingAt={Date.now()} />);

    expect(screen.getByText('상대가 입력 중…')).toBeTruthy();
  });

  it('표시된 후 3초가 지나면 인디케이터가 사라진다', () => {
    render(<TypingIndicator lastTypingAt={Date.now()} />);
    expect(screen.getByText('상대가 입력 중…')).toBeTruthy();

    act(() => {
      jest.advanceTimersByTime(3000);
    });

    expect(screen.queryByText('상대가 입력 중…')).toBeNull();
  });

  it('TTL 이내에 새 타이핑 이벤트가 오면 타이머가 갱신되어 계속 표시된다', () => {
    const firstTypingAt = Date.now();
    const { rerender } = render(<TypingIndicator lastTypingAt={firstTypingAt} />);

    act(() => {
      jest.advanceTimersByTime(2000);
    });
    rerender(<TypingIndicator lastTypingAt={firstTypingAt + 2000} />);

    act(() => {
      jest.advanceTimersByTime(2000);
    });

    expect(screen.getByText('상대가 입력 중…')).toBeTruthy();
  });
});
