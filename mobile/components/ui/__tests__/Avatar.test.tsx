/**
 * Avatar — 이름의 이니셜을 원형 플레이스홀더로 렌더한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { Avatar } from '../Avatar';

describe('Avatar', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('이름의 첫 글자를 이니셜로 렌더한다', () => {
    render(<Avatar name="김철수" />);

    expect(screen.getByText('김')).toBeTruthy();
  });

  it('accessibilityLabel에 이름이 포함된 아바타로 노출된다', () => {
    render(<Avatar name="이영희" />);

    expect(screen.getByLabelText('이영희 아바타')).toBeTruthy();
  });

  it('이름이 빈 문자열이면 물음표 플레이스홀더를 렌더한다', () => {
    render(<Avatar name="" />);

    expect(screen.getByText('?')).toBeTruthy();
  });
});
