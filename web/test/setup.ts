import "@testing-library/jest-dom";
import { vi } from "vitest";

// server-only 패키지는 Client Component 에서 import 시 throw 하도록 설계되어 있습니다.
// 테스트 환경(node)에서는 RSC 경계가 없으므로 빈 모듈로 대체합니다.
vi.mock("server-only", () => ({}));
