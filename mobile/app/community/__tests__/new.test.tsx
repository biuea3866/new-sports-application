/**
 * CommunityNewScreen(A-P3) — 게시글 작성 화면 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "게시글 작성".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import CommunityNewScreen from '../new';

jest.mock('../../../lib/usePosts', () => ({
  useCreatePost: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
  useLocalSearchParams: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCreatePost } from '../../../lib/usePosts';

const useCreatePostMock = useCreatePost as jest.MockedFunction<typeof useCreatePost>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

function forbiddenError(): AxiosError {
  return new AxiosError('Forbidden', undefined, undefined, undefined, {
    status: 403,
    data: {},
    statusText: 'Forbidden',
    headers: {},
    config: {} as never,
  });
}

function mockCreatePost(overrides: Record<string, unknown> = {}) {
  useCreatePostMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCreatePost>);
}

describe('CommunityNewScreen', () => {
  const backMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ back: backMock } as unknown as ReturnType<typeof useRouter>);
    useLocalSearchParamsMock.mockReturnValue({});
    mockCreatePost();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('communityId가 없으면(전역 게시글) 종목 선택 칩이 노출된다', () => {
    render(<CommunityNewScreen />);

    expect(screen.getByLabelText('종목 선택')).toBeTruthy();
  });

  it('communityId가 있으면(모임 게시글) 종목 선택 UI가 숨겨진다', () => {
    useLocalSearchParamsMock.mockReturnValue({ communityId: '5' });

    render(<CommunityNewScreen />);

    expect(screen.queryByLabelText('종목 선택')).toBeNull();
  });

  it('제목·내용이 비어 있으면 등록 CTA가 비활성 상태다', () => {
    render(<CommunityNewScreen />);

    const cta = screen.getByLabelText('게시글 등록');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('유효한 입력으로 제출하면 mutate가 호출되고 성공 시 뒤로 이동한다', () => {
    const mutate = jest.fn((_body: unknown, options?: { onSuccess?: () => void }) =>
      options?.onSuccess?.()
    );
    mockCreatePost({ mutate });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('제목 입력'), '토요일 경기 후기');
    fireEvent.changeText(screen.getByLabelText('내용 입력'), '오늘 경기 재밌었어요');
    fireEvent.press(screen.getByLabelText('게시글 등록'));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ title: '토요일 경기 후기', content: '오늘 경기 재밌었어요' }),
      expect.anything()
    );
    expect(backMock).toHaveBeenCalled();
  });

  it('모임 게시글 제출 시 communityId를 전송하고 sportCategory는 전송하지 않는다', () => {
    useLocalSearchParamsMock.mockReturnValue({ communityId: '5' });
    const mutate = jest.fn();
    mockCreatePost({ mutate });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('제목 입력'), '공지');
    fireEvent.changeText(screen.getByLabelText('내용 입력'), '내용');
    fireEvent.press(screen.getByLabelText('게시글 등록'));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ communityId: 5, sportCategory: undefined }),
      expect.anything()
    );
  });

  it('403 오류면 작성 권한이 없다는 안내가 표시된다', () => {
    const mutate = jest.fn((_body: unknown, options?: { onError?: (error: unknown) => void }) =>
      options?.onError?.(forbiddenError())
    );
    mockCreatePost({ mutate });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('제목 입력'), '제목');
    fireEvent.changeText(screen.getByLabelText('내용 입력'), '내용');
    fireEvent.press(screen.getByLabelText('게시글 등록'));

    expect(screen.getByText('작성 권한이 없어요')).toBeTruthy();
  });

  it('403이 아닌 오류면 일반 실패 안내가 표시된다', () => {
    const mutate = jest.fn((_body: unknown, options?: { onError?: (error: unknown) => void }) =>
      options?.onError?.(new Error('boom'))
    );
    mockCreatePost({ mutate });

    render(<CommunityNewScreen />);
    fireEvent.changeText(screen.getByLabelText('제목 입력'), '제목');
    fireEvent.changeText(screen.getByLabelText('내용 입력'), '내용');
    fireEvent.press(screen.getByLabelText('게시글 등록'));

    expect(screen.getByText('게시글 등록에 실패했습니다. 다시 시도해주세요.')).toBeTruthy();
  });

  it('취소를 탭하면 뒤로 이동한다', () => {
    render(<CommunityNewScreen />);
    fireEvent.press(screen.getByLabelText('뒤로 가기'));

    expect(backMock).toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    render(<CommunityNewScreen />);

    expect(screen.getByText('게시글 작성')).toBeTruthy();
  });
});
