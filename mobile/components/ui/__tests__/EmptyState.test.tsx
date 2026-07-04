/**
 * EmptyState — 전달된 안내 문구를 렌더하는 빈 상태 프레젠테이션.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { EmptyState } from '../EmptyState';

describe('EmptyState', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('전달된 안내 문구를 렌더한다', () => {
    render(<EmptyState message="참여 중인 채팅방이 없어요" />);

    expect(screen.getByText('참여 중인 채팅방이 없어요')).toBeTruthy();
  });

  it('description을 함께 전달하면 보조 설명도 렌더한다', () => {
    render(
      <EmptyState message="참여 중인 채팅방이 없어요" description="새로운 대화를 시작해보세요" />
    );

    expect(screen.getByText('새로운 대화를 시작해보세요')).toBeTruthy();
  });
});
