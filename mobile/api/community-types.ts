/**
 * 커뮤니티(동아리) 도메인 타입 정의
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "REST API 계약"·"응답 DTO 필드 스키마"
 *       (CommunityResponse/CommunityMemberResponse), `20260704-채팅시스템고도화-design-fe-app.md`
 *       "API 연동 표"(S3~S5, MembershipResponse).
 *
 * 기존 `api/types.ts`의 `post` 게시판 타입과 무관한 별개 도메인이다 — 이 파일에만 정의한다.
 */

// --- 유니온 타입 ---

/** 공개 여부. BE `CommunityVisibility` */
export type CommunityVisibility = 'PUBLIC' | 'PRIVATE';

/** 커뮤니티 내 역할. BE `CommunityRole` */
export type MemberRole = 'HOST' | 'MEMBER';

/** 멤버십 상태. BE `MembershipStatus` */
export type MembershipStatus = 'ACTIVE' | 'PENDING_APPROVAL' | 'LEFT' | 'KICKED';

/**
 * 스포츠 종목 카테고리. BE `domain/community/vo/SportCategory.kt`(BE-02, dev 머지) 확정 12개 값과
 * 1:1 정렬한다 — DB 컬럼은 VARCHAR(30)(ENUM 금지)이라 값 목록은 BE 도메인 enum이 SSOT다.
 * 분류에 없는 종목은 ETC로 지정한다 (BE-02 주석과 동일).
 */
export type SportCategory =
  | 'SOCCER'
  | 'BASKETBALL'
  | 'BASEBALL'
  | 'TENNIS'
  | 'BADMINTON'
  | 'GOLF'
  | 'RUNNING'
  | 'CYCLING'
  | 'SWIMMING'
  | 'HIKING'
  | 'YOGA'
  | 'ETC';

// --- Request ---

/** `POST /communities` 요청 본문. */
export interface CreateCommunityRequest {
  name: string;
  /** BE 확정 대기 — 응답(`CommunityResponse.description`)이 nullable이라 개설 시 생략 가능으로 취급 */
  description?: string;
  visibility: CommunityVisibility;
  sportCategory: SportCategory;
}

/** `POST /communities/{id}/host/transfer` 요청 본문. */
export interface TransferHostRequest {
  newHostUserId: number;
}

// --- Response ---

/**
 * `POST /communities`, `GET /communities*` 공용 응답.
 * 필드·Nullable은 TDD "CommunityResponse (`/communities*`)" 표를 그대로 따른다.
 */
export interface CommunityResponse {
  id: number;
  name: string;
  /** 설명 없을 수 있음 */
  description: string | null;
  visibility: CommunityVisibility;
  sportCategory: SportCategory;
  /** 방장 userId */
  hostUserId: number;
  /** 활성 멤버 수(조회 시 집계) */
  memberCount: number;
  /** 연결된 전용 그룹 방 id. 자동 생성 전/실패 시 null */
  roomId: number | null;
  /** 개설 시각, ISO-8601 */
  createdAt: string;
}

/**
 * `GET /communities/{id}/members`, `POST /communities/{id}/join`,
 * `POST /communities/{id}/members/{userId}/approve` 응답.
 * 필드·Nullable은 TDD "CommunityMemberResponse" 표를 그대로 따른다.
 */
export interface CommunityMemberResponse {
  id: number;
  communityId: number;
  userId: number;
  role: MemberRole;
  status: MembershipStatus;
  /** ACTIVE 전이 시각. PENDING_APPROVAL은 null */
  joinedAt: string | null;
}

/**
 * `POST /communities/{id}/join` 응답 중 FE가 가입 직후 분기(즉시 입장 vs 승인 대기)에
 * 필요한 최소 필드만 좁힌 뷰. BE는 `CommunityMemberResponse` 전체를 반환하되
 * FE는 이 좁혀진 상태만으로 UX 분기한다 (design-fe-app "API 연동 표" S5).
 */
export interface MembershipResponse {
  status: Extract<MembershipStatus, 'ACTIVE' | 'PENDING_APPROVAL'>;
}

// --- 소모임 예약(CommunityBooking) ---
// 근거: `20260707-모집-시설상품-소모임예약연동-tdd.md` "REST API 계약"·"응답 DTO"
// (CommunityBookingResponse/CommunityBookingListItemResponse),
// `application/community/dto/CommunityBookingResponse.kt`.

/** `POST /communities/{communityId}/bookings` 요청 본문. */
export interface LinkCommunityBookingRequest {
  slotId: number;
}

/** `POST /communities/{communityId}/bookings` 응답. */
export interface CommunityBookingResponse {
  id: number;
  communityId: number;
  slotId: number;
  linkedByUserId: number;
  createdAt: string;
}

/**
 * `GET /communities/{communityId}/bookings` 목록 응답 항목 — 연결된 slot의 시설·일시
 * 정보를 함께 담아 반환한다(facility/date/timeRange/capacity는 slot 조회 실패 시 null 가능).
 */
export interface CommunityBookingListItemResponse {
  id: number;
  communityId: number;
  slotId: number;
  linkedByUserId: number;
  facilityId: string | null;
  date: string | null;
  timeRange: string | null;
  capacity: number | null;
}
