/**
 * ScreenHeader — 루트 스택 상세 화면 공용 상단 헤더(뒤로가기 + 선택적 제목) 사용자 관점 검증.
 * 근거: 사용자 피드백 "채팅방·커뮤니티 상세에서 뒤로가기가 없어 갇힌다" — 루트 스택
 * headerShown:false 화면들이 자체 뒤로가기를 가져야 한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { ScreenHeader } from '../ScreenHeader';

describe('ScreenHeader', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('뒤로가기 버튼을 탭하면 onBack이 호출된다', () => {
    const onBack = jest.fn();
    render(<ScreenHeader onBack={onBack} />);

    fireEvent.press(screen.getByLabelText('뒤로 가기'));

    expect(onBack).toHaveBeenCalled();
  });

  it('title이 주어지면 제목을 렌더한다', () => {
    render(<ScreenHeader title="채팅방" onBack={jest.fn()} />);

    expect(screen.getByText('채팅방')).toBeTruthy();
  });

  it('title이 없으면 제목 없이 뒤로가기만 렌더한다', () => {
    render(<ScreenHeader onBack={jest.fn()} />);

    expect(screen.getByLabelText('뒤로 가기')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<ScreenHeader title="커뮤니티" onBack={jest.fn()} />);

    expect(screen.getByText('커뮤니티')).toBeTruthy();
  });
});
