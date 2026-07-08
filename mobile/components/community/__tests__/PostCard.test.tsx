/**
 * PostCard — 게시글 카드 사용자 관점 동작 검증.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { PostResponse } from '../../../api/types';
import { PostCard } from '../PostCard';

const BASE_POST: PostResponse = {
  id: 1,
  userId: 42,
  title: '토요일 경기 후기',
  type: 'FREE',
  createdAt: new Date().toISOString(),
  communityId: 5,
  sportCategory: 'SOCCER',
};

describe('PostCard', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('제목과 작성자·상대시간 메타를 렌더한다', () => {
    render(<PostCard post={BASE_POST} onPress={jest.fn()} />);

    expect(screen.getByText('토요일 경기 후기')).toBeTruthy();
    expect(screen.getByText(/사용자 42/)).toBeTruthy();
  });

  it('NOTICE 타입이면 공지 배지가 상단에 표시된다', () => {
    render(<PostCard post={{ ...BASE_POST, type: 'NOTICE' }} onPress={jest.fn()} />);

    expect(screen.getByText('📌 공지')).toBeTruthy();
  });

  it('FREE 타입이면 공지 배지가 표시되지 않는다', () => {
    render(<PostCard post={BASE_POST} onPress={jest.fn()} />);

    expect(screen.queryByText('📌 공지')).toBeNull();
  });

  it('카드를 탭하면 onPress가 호출된다', () => {
    const onPress = jest.fn();
    render(<PostCard post={BASE_POST} onPress={onPress} />);

    fireEvent.press(screen.getByLabelText(/게시글: 토요일 경기 후기/));

    expect(onPress).toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    render(<PostCard post={BASE_POST} onPress={jest.fn()} />);

    expect(screen.getByText('토요일 경기 후기')).toBeTruthy();
  });
});
