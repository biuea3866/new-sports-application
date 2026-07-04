/**
 * fake-stomp-client.ts — `@stomp/stompjs`의 `Client`를 대체하는 테스트 전용 모의 구현.
 *
 * `jest.mock('@stomp/stompjs', () => require('./test-helpers/fake-stomp-client'))` 형태로
 * 별도 모듈에서 `require`하면, jest.mock factory 안에서 외부 스코프 변수를 참조할 수
 * 없다는 제약(babel-plugin-jest-hoist)을 피할 수 있다.
 *
 * 실제 WebSocket 연결 없이 onConnect/onWebSocketClose/subscribe 콜백을
 * 테스트에서 직접 트리거하기 위한 헬퍼(simulateConnect 등)를 제공한다.
 */
import type { IMessage, StompConfig } from '@stomp/stompjs';

export interface FakeStompSubscription {
  unsubscribe: () => void;
}

export interface FakeStompClientInstance {
  config: StompConfig;
  connected: boolean;
  activateCallCount: number;
  deactivateCallCount: number;
  publishedFrames: { destination: string; body: string }[];
  activate: () => void;
  deactivate: () => Promise<void>;
  subscribe: (destination: string, callback: (message: IMessage) => void) => FakeStompSubscription;
  publish: (params: { destination: string; body: string }) => void;
  /** 테스트 전용 헬퍼: 브로커 연결 성공을 모사한다. */
  simulateConnect: () => void;
  /** 테스트 전용 헬퍼: WebSocket 종료(연결 끊김)를 모사한다. */
  simulateClose: () => void;
  /** 테스트 전용 헬퍼: 특정 destination 구독 콜백에 메시지를 흘려보낸다. */
  simulateMessage: (destination: string, body: unknown) => void;
}

/** 생성된 모든 FakeStompClient 인스턴스. 테스트에서 최신 인스턴스를 조회할 때 사용한다. */
export const mockStompClientInstances: FakeStompClientInstance[] = [];

export class Client implements FakeStompClientInstance {
  config: StompConfig;
  connected = false;
  activateCallCount = 0;
  deactivateCallCount = 0;
  publishedFrames: { destination: string; body: string }[] = [];
  private readonly subscriptionCallbacks = new Map<string, (message: IMessage) => void>();

  constructor(config: StompConfig) {
    this.config = config;
    mockStompClientInstances.push(this);
  }

  activate(): void {
    this.activateCallCount += 1;
  }

  deactivate(): Promise<void> {
    this.deactivateCallCount += 1;
    return Promise.resolve();
  }

  subscribe(destination: string, callback: (message: IMessage) => void): FakeStompSubscription {
    this.subscriptionCallbacks.set(destination, callback);
    return { unsubscribe: jest.fn() };
  }

  publish(params: { destination: string; body: string }): void {
    this.publishedFrames.push(params);
  }

  simulateConnect(): void {
    this.connected = true;
    this.config.onConnect?.({
      headers: {},
      command: 'CONNECTED',
      body: '',
      binaryBody: new Uint8Array(),
    } as never);
  }

  simulateClose(): void {
    this.connected = false;
    this.config.onWebSocketClose?.({} as never);
  }

  simulateMessage(destination: string, body: unknown): void {
    const callback = this.subscriptionCallbacks.get(destination);
    if (!callback) {
      throw new Error(`구독되지 않은 destination: ${destination}`);
    }
    callback({ body: JSON.stringify(body) } as IMessage);
  }
}

export enum ReconnectionTimeMode {
  LINEAR = 'LINEAR',
  EXPONENTIAL = 'EXPONENTIAL',
}
