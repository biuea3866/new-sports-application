/**
 * MessageComposer — 단일 입력 CTA. 읽기전용 게스트(canSpeak=false)는 입력창 비활성 + 안내.
 * 근거: FE-10 티켓 테스트 케이스 "읽기 전용 게스트는 입력창이 비활성이고 안내가 표시된다".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { MessageComposer } from '../MessageComposer';

describe('MessageComposer', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('canSpeak=false이면 입력창이 비활성이고 읽기 전용 안내가 표시된다', () => {
    render(<MessageComposer canSpeak={false} onSend={jest.fn()} onTypingChange={jest.fn()} />);

    expect(screen.getByLabelText('메시지 입력')).toHaveProp('editable', false);
    expect(screen.getByText('읽기 전용으로 초대되었어요')).toBeTruthy();
    expect(screen.getByRole('button', { name: '메시지 전송' })).toBeDisabled();
  });

  it('canSpeak=true이고 입력이 비어 있으면 전송 버튼이 비활성이다', () => {
    render(<MessageComposer canSpeak onSend={jest.fn()} onTypingChange={jest.fn()} />);

    expect(screen.getByRole('button', { name: '메시지 전송' })).toBeDisabled();
  });

  it('텍스트를 입력하면 onTypingChange(true)가 호출된다', () => {
    const onTypingChange = jest.fn();
    render(<MessageComposer canSpeak onSend={jest.fn()} onTypingChange={onTypingChange} />);

    fireEvent.changeText(screen.getByLabelText('메시지 입력'), '안녕하세요');

    expect(onTypingChange).toHaveBeenCalledWith(true);
  });

  it('전송 버튼을 탭하면 onSend가 트림된 내용으로 호출되고 입력이 비워진다', () => {
    const onSend = jest.fn();
    const onTypingChange = jest.fn();
    render(<MessageComposer canSpeak onSend={onSend} onTypingChange={onTypingChange} />);

    const input = screen.getByLabelText('메시지 입력');
    fireEvent.changeText(input, '  안녕하세요  ');
    fireEvent.press(screen.getByRole('button', { name: '메시지 전송' }));

    expect(onSend).toHaveBeenCalledWith('안녕하세요');
    expect(onTypingChange).toHaveBeenCalledWith(false);
    expect(input).toHaveProp('value', '');
  });

  it('sending=true이면 전송 버튼이 비활성된다', () => {
    render(<MessageComposer canSpeak sending onSend={jest.fn()} onTypingChange={jest.fn()} />);

    fireEvent.changeText(screen.getByLabelText('메시지 입력'), '안녕하세요');

    expect(screen.getByRole('button', { name: '메시지 전송' })).toBeDisabled();
  });
});
