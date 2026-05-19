// @testing-library/react-native 12.4+ 이상에서는 내장 matchers 사용
// extend-expect는 더 이상 별도 import가 필요하지 않습니다

// expo-secure-store mock
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

// expo-router mock
jest.mock('expo-router', () => ({
  useRouter: jest.fn(() => ({
    push: jest.fn(),
    replace: jest.fn(),
    back: jest.fn(),
  })),
  useSegments: jest.fn(() => []),
  useLocalSearchParams: jest.fn(() => ({})),
  Link: 'Link',
  Stack: {
    Screen: 'Screen',
  },
  Tabs: {
    Screen: 'Screen',
  },
}));

// react-native-mmkv mock (네이티브 모듈 없이 테스트 가능하도록)
// jest.mock factory 안에서 외부 변수를 참조할 수 없으므로 factory 내부에서 Map을 생성합니다.
jest.mock('react-native-mmkv', () => {
  const mockStorage = new Map<string, string | boolean | number | ArrayBuffer>();
  return {
    MMKV: jest.fn().mockImplementation(() => ({
      getString: (key: string) => {
        const v = mockStorage.get(key);
        return typeof v === 'string' ? v : undefined;
      },
      set: (key: string, value: string | boolean | number | ArrayBuffer) =>
        mockStorage.set(key, value),
      delete: (key: string) => mockStorage.delete(key),
      clearAll: () => mockStorage.clear(),
      contains: (key: string) => mockStorage.has(key),
      getAllKeys: () => Array.from(mockStorage.keys()),
      getBoolean: (key: string) => {
        const v = mockStorage.get(key);
        return typeof v === 'boolean' ? v : undefined;
      },
      getNumber: (key: string) => {
        const v = mockStorage.get(key);
        return typeof v === 'number' ? v : undefined;
      },
      getBuffer: (_key: string) => undefined,
      recrypt: () => undefined,
      trim: () => undefined,
      size: 0,
      isReadOnly: false,
      addOnValueChangedListener: () => ({ remove: () => undefined }),
    })),
  };
});

// @react-native-community/netinfo mock
jest.mock('@react-native-community/netinfo', () => ({
  useNetInfo: jest.fn(() => ({
    type: 'wifi',
    isConnected: true,
    isInternetReachable: true,
    details: null,
  })),
  fetch: jest.fn(() =>
    Promise.resolve({
      type: 'wifi',
      isConnected: true,
      isInternetReachable: true,
      details: null,
    })
  ),
}));

// Silence console.error for known React Native warnings in tests
const originalConsoleError = console.error;
console.error = (...args: unknown[]) => {
  const message = typeof args[0] === 'string' ? args[0] : '';
  if (
    message.includes('Warning: ReactDOM.render is no longer supported') ||
    message.includes('Warning: An update to') ||
    message.includes('act(...)')
  ) {
    return;
  }
  originalConsoleError(...args);
};
