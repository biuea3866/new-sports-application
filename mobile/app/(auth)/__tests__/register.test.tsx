/**
 * RegisterScreen — 닉네임 입력 배선 검증.
 * 소셜 화면(게시글 작성자·방장·초대자·신청자)이 사람이 읽는 이름을 보여주려면 가입 시점에
 * 닉네임을 받아야 한다. 화면이 닉네임을 요구하고 등록 요청에 실어 보내는지 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
  Link: ({ children }: { children: React.ReactNode }) => children,
}));

jest.mock('../../../lib/auth', () => ({
  useAuthStore: jest.fn(),
}));

jest.mock('../../../api/be-client', () => ({
  getBeClient: jest.fn(),
}));

import { useRouter } from 'expo-router';
import { useAuthStore } from '../../../lib/auth';
import { getBeClient } from '../../../api/be-client';
import RegisterScreen from '../register';

const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const useAuthStoreMock = useAuthStore as unknown as jest.Mock;
const getBeClientMock = getBeClient as jest.MockedFunction<typeof getBeClient>;

describe('회원가입 화면 — 닉네임', () => {
  const postMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({
      replace: jest.fn(),
      push: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    useAuthStoreMock.mockImplementation(
      (selector: (s: { setTokens: () => Promise<void> }) => unknown) =>
        selector({ setTokens: jest.fn().mockResolvedValue(undefined) })
    );
    postMock.mockResolvedValue({ data: { accessToken: 'a', refreshToken: 'r' } });
    getBeClientMock.mockReturnValue({ post: postMock } as unknown as ReturnType<
      typeof getBeClient
    >);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('닉네임 입력란을 제공한다', () => {
    render(<RegisterScreen />);

    expect(screen.getByLabelText('닉네임 입력')).toBeTruthy();
  });

  it('닉네임을 비우고 가입하면 안내 문구를 보여주고 요청하지 않는다', async () => {
    render(<RegisterScreen />);

    fireEvent.changeText(screen.getByLabelText('이메일 입력'), 'new@example.com');
    fireEvent.changeText(screen.getByLabelText('비밀번호 입력'), 'password1234');
    fireEvent.press(screen.getByLabelText('회원가입'));

    await waitFor(() => {
      expect(screen.getByText(/닉네임/)).toBeTruthy();
    });
    expect(postMock).not.toHaveBeenCalled();
  });

  it('닉네임을 함께 등록 요청에 실어 보낸다', async () => {
    render(<RegisterScreen />);

    fireEvent.changeText(screen.getByLabelText('이메일 입력'), 'new@example.com');
    fireEvent.changeText(screen.getByLabelText('닉네임 입력'), '김철수');
    fireEvent.changeText(screen.getByLabelText('비밀번호 입력'), 'password1234');
    fireEvent.press(screen.getByLabelText('회원가입'));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith('/users/register', {
        email: 'new@example.com',
        password: 'password1234',
        nickname: '김철수',
      });
    });
  });

  // BE 는 Spring ProblemDetail 로 내려주고 code 는 최상위로 평탄화된다
  // (setProperty("code", ...) + ProblemDetailJacksonMixin 의 @JsonAnyGetter, BE 통합 테스트가 `$.code` 로 검증).
  // 이 케이스는 구 형태(properties 중첩) 응답도 읽는 클라이언트 폴백을 함께 고정한다.
  // 더블이 계약을 발명하지 않도록 실제 응답 모양을 그대로 쓴다.
  function problemDetailError(status: number, code: string) {
    return new AxiosError('boom', undefined, undefined, undefined, {
      status,
      data: {
        type: `https://errors.sports-application/${code.toLowerCase().replace(/_/g, '-')}`,
        title: 'Invalid Nickname',
        status,
        detail: `Invalid nickname: x`,
        properties: { code },
      },
      statusText: '',
      headers: {},
      config: {} as never,
    });
  }

  it('닉네임 규칙 위반(400)이면 규칙 안내 문구를 보여준다', async () => {
    postMock.mockRejectedValueOnce(problemDetailError(400, 'INVALID_NICKNAME'));

    render(<RegisterScreen />);

    fireEvent.changeText(screen.getByLabelText('이메일 입력'), 'new@example.com');
    fireEvent.changeText(screen.getByLabelText('닉네임 입력'), 'x');
    fireEvent.changeText(screen.getByLabelText('비밀번호 입력'), 'password1234');
    fireEvent.press(screen.getByLabelText('회원가입'));

    await waitFor(() => {
      expect(screen.getByText(/닉네임은 한글·영문·숫자·밑줄 2~20자/)).toBeTruthy();
    });
  });

  it('다크 모드에서도 닉네임 입력란이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<RegisterScreen />);

    expect(screen.getByLabelText('닉네임 입력')).toBeTruthy();
  });
});
