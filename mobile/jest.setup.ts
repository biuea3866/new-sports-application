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
