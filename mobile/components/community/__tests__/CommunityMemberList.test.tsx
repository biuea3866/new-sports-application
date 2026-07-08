/**
 * CommunityMemberList — 역할 배지 + 방장 전용 강퇴·위임 액션 사용자 관점 동작 검증.
 * 근거: FE-12 티켓 "테스트 케이스", design-fe-app.md S5 와이어프레임(FR-3).
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityMemberResponse } from '../../../api/community-types';
import { CommunityMemberList } from '../CommunityMemberList';

function member(overrides: Partial<CommunityMemberResponse> = {}): CommunityMemberResponse {
  return {
    id: 1,
    communityId: 1,
    userId: 100,
    role: 'MEMBER',
    status: 'ACTIVE',
    joinedAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

describe('CommunityMemberList', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('멤버가 없으면 "멤버가 없어요"를 표시한다', () => {
    render(
      <CommunityMemberList
        members={[]}
        canManage={false}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.getByText('멤버가 없어요')).toBeTruthy();
  });

  it('멤버 수를 제목에 표시한다', () => {
    render(
      <CommunityMemberList
        members={[member({ id: 1, userId: 1, role: 'HOST' }), member({ id: 2, userId: 2 })]}
        canManage={false}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.getByText('멤버 (2)')).toBeTruthy();
  });

  it('방장에게는 다른 멤버별 강퇴·위임 버튼이 노출되고 탭하면 콜백을 호출한다', () => {
    const onKick = jest.fn();
    const onTransfer = jest.fn();
    render(
      <CommunityMemberList
        members={[
          member({ id: 1, userId: 1, role: 'HOST' }),
          member({ id: 2, userId: 2, role: 'MEMBER' }),
        ]}
        canManage={true}
        onKick={onKick}
        onTransfer={onTransfer}
      />
    );

    fireEvent.press(screen.getByLabelText('사용자 #2 강퇴'));
    expect(onKick).toHaveBeenCalledWith(expect.objectContaining({ userId: 2 }));

    fireEvent.press(screen.getByLabelText('사용자 #2 방장 위임'));
    expect(onTransfer).toHaveBeenCalledWith(expect.objectContaining({ userId: 2 }));
  });

  it('일반 멤버(canManage=false)에게는 강퇴·위임 버튼이 노출되지 않는다', () => {
    render(
      <CommunityMemberList
        members={[
          member({ id: 1, userId: 1, role: 'HOST' }),
          member({ id: 2, userId: 2, role: 'MEMBER' }),
        ]}
        canManage={false}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.queryByLabelText('사용자 #2 강퇴')).toBeNull();
    expect(screen.queryByLabelText('사용자 #2 방장 위임')).toBeNull();
  });

  it('방장 자신의 행에는 강퇴·위임 버튼이 노출되지 않는다', () => {
    render(
      <CommunityMemberList
        members={[member({ id: 1, userId: 1, role: 'HOST' })]}
        canManage={true}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.queryByLabelText('사용자 #1 강퇴')).toBeNull();
  });

  it('각 멤버의 역할 배지를 표시한다', () => {
    render(
      <CommunityMemberList
        members={[
          member({ id: 1, userId: 1, role: 'HOST' }),
          member({ id: 2, userId: 2, role: 'MEMBER' }),
        ]}
        canManage={false}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.getByLabelText('방장')).toBeTruthy();
    expect(screen.getByLabelText('멤버')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    render(
      <CommunityMemberList
        members={[member({ id: 1, userId: 1, role: 'HOST' })]}
        canManage={true}
        onKick={jest.fn()}
        onTransfer={jest.fn()}
      />
    );

    expect(screen.getByText('멤버 (1)')).toBeTruthy();
  });
});
