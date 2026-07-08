/**
 * CommunitySummary — 상단 요약 + 역할별 단일 주요 CTA 사용자 관점 동작 검증.
 * 근거: FE-12 티켓 "테스트 케이스", design-fe-app.md S5 와이어프레임·상태 표.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityResponse } from '../../../api/community-types';
import { CommunitySummary } from '../CommunitySummary';

const baseCommunity: CommunityResponse = {
  id: 1,
  name: '주말 축구 모임',
  description: '동네에서 주말마다 축구해요',
  visibility: 'PUBLIC',
  sportCategory: 'SOCCER',
  hostUserId: 10,
  memberCount: 32,
  roomId: 99,
  createdAt: '2026-07-01T00:00:00Z',
};

describe('CommunitySummary', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('비멤버에게는 가입하기 버튼을 보여주고 탭하면 onJoin을 호출한다', () => {
    const onJoin = jest.fn();
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'non-member' }}
        onJoin={onJoin}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    fireEvent.press(screen.getByLabelText('가입하기'));
    expect(onJoin).toHaveBeenCalled();
  });

  it('승인 대기 중인 뷰어에게는 비활성 "승인 대기 중" CTA를 보여준다', () => {
    render(
      <CommunitySummary
        community={{ ...baseCommunity, visibility: 'PRIVATE' }}
        viewer={{ kind: 'pending' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    const cta = screen.getByLabelText('승인 대기 중');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('멤버에게는 채팅 입장 버튼을 보여주고 탭하면 onEnterChat을 호출한다', () => {
    const onEnterChat = jest.fn();
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'member', role: 'MEMBER' }}
        onJoin={jest.fn()}
        onEnterChat={onEnterChat}
        isJoinPending={false}
      />
    );

    fireEvent.press(screen.getByLabelText('채팅 입장'));
    expect(onEnterChat).toHaveBeenCalled();
  });

  it('방장에게도 채팅 입장 버튼을 보여준다', () => {
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'host' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    expect(screen.getByLabelText('채팅 입장')).toBeTruthy();
  });

  it('전용 방이 없으면(roomId null) 채팅 입장 버튼을 비활성 처리한다', () => {
    render(
      <CommunitySummary
        community={{ ...baseCommunity, roomId: null }}
        viewer={{ kind: 'member', role: 'MEMBER' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    expect(screen.getByLabelText('채팅 입장').props.accessibilityState.disabled).toBe(true);
  });

  it('종목·공개여부·멤버수·방장을 요약으로 표시한다', () => {
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'non-member' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    expect(screen.getByText('축구 · 공개 · 멤버 32명')).toBeTruthy();
    expect(screen.getByText('방장 #10')).toBeTruthy();
    expect(screen.getByText(baseCommunity.description as string)).toBeTruthy();
  });

  it('가입 처리 중이면 가입하기 버튼이 busy 상태다', () => {
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'non-member' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={true}
      />
    );

    expect(screen.getByLabelText('가입하기').props.accessibilityState.busy).toBe(true);
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    render(
      <CommunitySummary
        community={baseCommunity}
        viewer={{ kind: 'non-member' }}
        onJoin={jest.fn()}
        onEnterChat={jest.fn()}
        isJoinPending={false}
      />
    );

    expect(screen.getByLabelText('가입하기')).toBeTruthy();
  });
});
