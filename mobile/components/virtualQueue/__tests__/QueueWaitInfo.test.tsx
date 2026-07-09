/**
 * QueueWaitInfo — 앞선 대기 인원·예상 대기 ETA 보조 텍스트를 렌더합니다.
 * 값이 null이면 해당 줄을 렌더하지 않습니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { QueueWaitInfo } from '../QueueWaitInfo';

describe('QueueWaitInfo', () => {
  it('앞선 대기 인원과 ETA를 모두 렌더한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueWaitInfo aheadCount={1239} etaLabel="약 4분" />);

    expect(screen.getByText('앞선 대기 1,239명')).toBeTruthy();
    expect(screen.getByText('예상 대기 약 4분')).toBeTruthy();
  });

  it('etaLabel이 null이면 ETA 줄을 렌더하지 않는다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueWaitInfo aheadCount={10} etaLabel={null} />);

    expect(screen.getByText('앞선 대기 10명')).toBeTruthy();
    expect(screen.queryByText(/예상 대기/)).toBeNull();
  });

  it('aheadCount가 null이면 앞선 대기 줄을 렌더하지 않는다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueWaitInfo aheadCount={null} etaLabel="약 1분" />);

    expect(screen.queryByText(/앞선 대기/)).toBeNull();
    expect(screen.getByText('예상 대기 약 1분')).toBeTruthy();
  });

  it('둘 다 null이면 아무 줄도 렌더하지 않는다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueWaitInfo aheadCount={null} etaLabel={null} />);

    expect(screen.queryByText(/앞선 대기/)).toBeNull();
    expect(screen.queryByText(/예상 대기/)).toBeNull();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<QueueWaitInfo aheadCount={5} etaLabel="약 1분" />);

    expect(screen.getByText('앞선 대기 5명')).toBeTruthy();
  });
});
