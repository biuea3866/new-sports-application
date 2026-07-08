/**
 * MessageBubble — 내/상대 메시지 정렬·읽음 표시·시각 렌더 검증.
 * 근거: FE-10 티켓 테스트 케이스 "내 메시지는 우측, 상대 메시지는 좌측 정렬로 렌더된다".
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { MessageBubble } from '../MessageBubble';
import { lightTokens } from '../../../theme/tokens';
import type { MessageResponse } from '../../../api/types';

const baseMessage: MessageResponse = {
  id: 1,
  roomId: 10,
  senderId: 2,
  content: '오늘 몇 시에 모여요?',
  sentAt: '2026-07-06T05:20:00.000Z',
};

describe('MessageBubble', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('내 메시지는 우측 정렬(bubbleMine 배경)로 렌더된다', () => {
    render(<MessageBubble message={baseMessage} isMine isRead={false} />);

    expect(screen.getByTestId('message-bubble-row')).toHaveStyle({ alignItems: 'flex-end' });
    expect(screen.getByTestId('message-bubble')).toHaveStyle({
      backgroundColor: lightTokens.bubbleMine,
    });
    expect(screen.getByText('오늘 몇 시에 모여요?')).toBeTruthy();
  });

  it('상대 메시지는 좌측 정렬(bubbleOther 배경)로 렌더된다', () => {
    render(<MessageBubble message={baseMessage} isMine={false} isRead={false} />);

    expect(screen.getByTestId('message-bubble-row')).toHaveStyle({ alignItems: 'flex-start' });
    expect(screen.getByTestId('message-bubble')).toHaveStyle({
      backgroundColor: lightTokens.bubbleOther,
    });
  });

  it('내 메시지이고 읽음이면 읽음 표시가 렌더된다', () => {
    render(<MessageBubble message={baseMessage} isMine isRead />);

    expect(screen.getByText('읽음')).toBeTruthy();
  });

  it('내 메시지이지만 아직 읽지 않았으면 읽음 표시가 렌더되지 않는다', () => {
    render(<MessageBubble message={baseMessage} isMine isRead={false} />);

    expect(screen.queryByText('읽음')).toBeNull();
  });

  it('상대 메시지는 읽음 여부와 무관하게 읽음 표시가 렌더되지 않는다', () => {
    render(<MessageBubble message={baseMessage} isMine={false} isRead />);

    expect(screen.queryByText('읽음')).toBeNull();
  });
});
