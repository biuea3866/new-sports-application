/**
 * B2B Portal zod 스키마.
 * BE Response DTO 검증 및 입력 검증에 사용한다.
 */
import { z } from "zod";

// ─── 공통 ───────────────────────────────────────────────────────────────────

// BE는 Spring Data `Page`를 반환한다 (페이지 인덱스 필드명이 `number`, `page` 아님).
// `number`(Spring)와 `page`(혹시 모를 커스텀) 양쪽을 수용해 FE 표준 `page`로 정규화한다.
export const PageSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z
    .object({
      content: z.array(itemSchema),
      number: z.number().int().nonnegative().optional(),
      page: z.number().int().nonnegative().optional(),
      size: z.number().int().positive(),
      totalElements: z.number().int().nonnegative(),
      totalPages: z.number().int().nonnegative(),
    })
    .transform((p) => ({
      content: p.content,
      page: p.page ?? p.number ?? 0,
      size: p.size,
      totalElements: p.totalElements,
      totalPages: p.totalPages,
    }));

// ─── Facility ────────────────────────────────────────────────────────────────

const FacilityTypeSchema = z.enum(["INDOOR", "OUTDOOR", "MIXED"]);

export const MyFacilitySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  gu: z.string(),
  sidoCode: z.string(),
  sidoName: z.string(),
  sigunguCode: z.string(),
  sigunguName: z.string(),
  type: FacilityTypeSchema,
  address: z.string(),
  lat: z.number(),
  lng: z.number(),
  parking: z.boolean(),
  tel: z.string(),
  homePage: z.string().nullable(),
  eduYn: z.boolean(),
  meta: z.string().nullable(),
  ownerUserId: z.number().int(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const MyFacilityPageSchema = PageSchema(MyFacilitySchema);

export const CreateFacilityInputSchema = z.object({
  code: z.string().min(1),
  name: z.string().min(1),
  gu: z.string().min(1),
  sido: z.string().optional(),
  type: FacilityTypeSchema,
  address: z.string().min(1),
  location: z.string().min(1),
  parking: z.boolean(),
  tel: z.string().min(1),
  homePage: z.string().optional(),
  eduYn: z.boolean(),
  meta: z.string().optional(),
});

export const UpdateFacilityInputSchema = z
  .object({
    name: z.string().min(1).optional(),
    gu: z.string().min(1).optional(),
    sido: z.string().optional(),
    type: FacilityTypeSchema.optional(),
    address: z.string().min(1).optional(),
    location: z.string().min(1).optional(),
    parking: z.boolean().optional(),
    tel: z.string().min(1).optional(),
    homePage: z.string().optional(),
    eduYn: z.boolean().optional(),
    meta: z.string().optional(),
  })
  .refine((data) => Object.keys(data).length > 0, {
    message: "수정할 필드가 최소 1개 이상 있어야 합니다.",
  });

// ─── Event ───────────────────────────────────────────────────────────────────

const EventStatusSchema = z.enum(["SCHEDULED", "OPEN", "CLOSED", "CANCELLED"]);

export const SeatInfoSchema = z.object({
  id: z.number().int(),
  label: z.string(),
  sold: z.boolean(),
});

export const MyEventSchema = z.object({
  id: z.number().int(),
  title: z.string(),
  venue: z.string(),
  startsAt: z.string(),
  status: EventStatusSchema,
  ownerId: z.number().int(),
  totalSeats: z.number().int().nonnegative(),
  soldSeats: z.number().int().nonnegative(),
  availableSeats: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const MyEventDetailSchema = MyEventSchema.extend({
  seats: z.array(SeatInfoSchema),
});

export const MyEventPageSchema = PageSchema(MyEventSchema);

export const CreateEventInputSchema = z.object({
  title: z.string().min(1),
  venue: z.string().min(1),
  startsAt: z.string().datetime(),
  seats: z.array(z.string().min(1)).min(1).max(500, {
    message: "좌석은 최대 500개까지 등록할 수 있습니다.",
  }),
  price: z.number().int().positive({ message: "좌석 가격을 입력해 주세요." }),
  section: z.string().min(1).default("GENERAL"),
});

// ─── Product ─────────────────────────────────────────────────────────────────

const ProductStatusSchema = z.enum(["ACTIVE", "INACTIVE"]);
const ProductCategorySchema = z.enum(["EQUIPMENT", "APPAREL", "FOOTWEAR", "ACCESSORY"]);

export const MyProductSchema = z.object({
  id: z.number().int(),
  name: z.string(),
  description: z.string(),
  price: z.number().positive(),
  category: ProductCategorySchema,
  imageUrl: z.string(),
  status: ProductStatusSchema,
  stockQuantity: z.number().int().nonnegative(),
  // 목록 응답에는 아래 필드가 없을 수 있다 (상세 응답에만 포함). optional 처리.
  ownerId: z.number().int().optional(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
});

export const MyProductPageSchema = PageSchema(MyProductSchema);

export const CreateProductInputSchema = z.object({
  name: z.string().min(1),
  description: z.string().min(1),
  price: z.number().int().positive(),
  category: ProductCategorySchema,
  imageUrl: z.string().url(),
});

export const UpdateProductInputSchema = z
  .object({
    name: z.string().min(1).optional(),
    description: z.string().min(1).optional(),
    price: z.number().int().positive().optional(),
    category: ProductCategorySchema.optional(),
    imageUrl: z.string().url().optional(),
  })
  .refine((data) => Object.keys(data).length > 0, {
    message: "수정할 필드가 최소 1개 이상 있어야 합니다.",
  });

export const RestoreStockInputSchema = z.object({
  quantity: z.number().int().positive({
    message: "재고 수량은 1 이상이어야 합니다.",
  }),
});

// ─── AdminUser ───────────────────────────────────────────────────────────────

const UserStatusSchema = z.enum(["ACTIVE", "INACTIVE", "SUSPENDED"]);

export const AdminUserSchema = z.object({
  userId: z.number().int().positive(),
  email: z.string().email(),
  status: UserStatusSchema,
  roleNames: z.array(z.string()),
  joinedAt: z.string(),
});

export const AdminUserPageSchema = PageSchema(AdminUserSchema);


// ─── Notification ────────────────────────────────────────────────────────────

const NotificationChannelSchema = z.enum(["IN_APP", "EMAIL", "SMS", "PUSH"]);
const NotificationStatusSchema = z.enum(["QUEUED", "SENT", "FAILED", "DELIVERED"]);

export const NotificationSchema = z.object({
  id: z.number().int().positive(),
  userId: z.number().int().positive(),
  channel: NotificationChannelSchema,
  templateId: z.string(),
  status: NotificationStatusSchema,
  sentAt: z.string().nullable(),
  readAt: z.string().nullable(),
  createdAt: z.string(),
});

export const NotificationPageSchema = z.object({
  content: z.array(NotificationSchema),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
});

// ─── Booking ─────────────────────────────────────────────────────────────────

const BookingStatusSchema = z.enum(["PENDING", "CONFIRMED", "CANCELLED", "EXPIRED"]);

export const BookingSchema = z.object({
  id: z.number().int(),
  slotId: z.number().int(),
  userId: z.number().int(),
  status: BookingStatusSchema,
  paymentId: z.number().int().nullable(),
  paymentStatus: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const BookingListResponseSchema = z.object({
  bookings: z.array(BookingSchema),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
});

export const CancelBookingInputSchema = z.object({
  reason: z.string().optional(),
});

// ─── Payment ─────────────────────────────────────────────────────────────────

export const PaymentStatusSchema = z.enum(["PENDING", "COMPLETED", "FAILED", "REFUNDED"]);
export const PaymentMethodSchema = z.enum([
  "CREDIT_CARD",
  "BANK_TRANSFER",
  "KAKAO",
  "TOSS",
  "NAVER",
  "DANAL",
]);
export const OrderTypeSchema = z.enum(["BOOKING", "TICKETING", "GOODS"]);

export const PaymentSummarySchema = z.object({
  id: z.number().int().positive(),
  orderType: OrderTypeSchema,
  orderId: z.number().int().positive(),
  method: PaymentMethodSchema,
  amount: z.number(),
  status: PaymentStatusSchema,
  createdAt: z.string(),
  paidAt: z.string().nullable(),
  pgTransactionId: z.string().nullable().optional(),
  provider: z.string().nullable().optional(),
});

export const PaymentSummaryPageSchema = PageSchema(PaymentSummarySchema);

// ─── Facility Schedule (운영시간 / 휴무일) ─────────────────────────────────────
// BE 계약: FacilityScheduleApiController. 시간은 "HH:mm" 또는 "HH:mm:ss"(LocalTime.toString()),
// 날짜는 "yyyy-MM-dd"(LocalDate.toString()) 문자열이다.

const TIME_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)(:[0-5]\d)?$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export const DayOfWeekSchema = z.enum([
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
]);

export const TimeRangeSchema = z.object({
  start: z.string(),
  end: z.string(),
});

export const OperatingHoursSchema = z.object({
  dayOfWeek: DayOfWeekSchema,
  openTime: z.string(),
  closeTime: z.string(),
  breaks: z.array(TimeRangeSchema),
  slotDurationMinutes: z.number().int().positive(),
  capacity: z.number().int().positive(),
});

// 시설 상세 응답(FacilityResponse) 중 이 설계가 필요로 하는 부분만 파싱한다.
// 나머지 필드(name·address 등)는 zod가 알아서 무시한다.
export const FacilityScheduleSchema = z.object({
  id: z.string(),
  operatingHours: z.array(OperatingHoursSchema),
  holidays: z.array(z.string()),
});

export const TimeRangeInputSchema = z
  .object({
    start: z.string().regex(TIME_PATTERN, { message: "start는 HH:mm 형식이어야 합니다." }),
    end: z.string().regex(TIME_PATTERN, { message: "end는 HH:mm 형식이어야 합니다." }),
  })
  .refine((data) => data.start < data.end, {
    message: "브레이크 시작 시각은 종료 시각보다 빨라야 합니다.",
    path: ["end"],
  });

export const OperatingHoursInputSchema = z
  .object({
    dayOfWeek: DayOfWeekSchema,
    openTime: z.string().regex(TIME_PATTERN, { message: "openTime은 HH:mm 형식이어야 합니다." }),
    closeTime: z.string().regex(TIME_PATTERN, { message: "closeTime은 HH:mm 형식이어야 합니다." }),
    breaks: z.array(TimeRangeInputSchema).default([]),
    slotDurationMinutes: z
      .number()
      .int()
      .positive({ message: "슬롯 단위는 1분 이상이어야 합니다." })
      .default(60),
    capacity: z.number().int().positive({ message: "정원은 1 이상이어야 합니다." }),
  })
  .refine((data) => data.openTime < data.closeTime, {
    message: "오픈 시각은 마감 시각보다 빨라야 합니다.",
    path: ["closeTime"],
  });

export const RegisterOperatingHoursInputSchema = z.object({
  operatingHours: z.array(OperatingHoursInputSchema).min(1, {
    message: "운영시간을 최소 1개 이상 등록해야 합니다.",
  }),
});

export const HolidayInputSchema = z.object({
  date: z.string().regex(DATE_PATTERN, { message: "date는 yyyy-MM-dd 형식이어야 합니다." }),
});

// ─── Program (시설상품) ─────────────────────────────────────────────────────────

export const ProgramSchema = z.object({
  id: z.number().int(),
  facilityId: z.string(),
  ownerUserId: z.number().int(),
  name: z.string(),
  description: z.string().nullable(),
  price: z.number().nonnegative(),
  capacity: z.number().int().positive(),
  durationMinutes: z.number().int().positive(),
});

export const ProgramListSchema = z.array(ProgramSchema);

export const CreateProgramInputSchema = z.object({
  name: z.string().min(1, { message: "이름을 입력해 주세요." }),
  description: z.string().optional(),
  price: z.number().nonnegative({ message: "가격은 0 이상이어야 합니다." }),
  capacity: z.number().int().positive({ message: "정원은 1 이상이어야 합니다." }),
  durationMinutes: z.number().int().positive({ message: "소요 시간은 1분 이상이어야 합니다." }),
});

// ─── Slot 상태 (open/close) ─────────────────────────────────────────────────────

export const SlotStatusSchema = z.enum(["OPEN", "CLOSED"]);

export const SlotSchema = z.object({
  id: z.number().int(),
  facilityId: z.string(),
  date: z.string(),
  timeRange: z.string(),
  capacity: z.number().int(),
  ownerId: z.number().int(),
  status: SlotStatusSchema,
  programId: z.number().int().nullable(),
});

// ─── Dashboard ───────────────────────────────────────────────────────────────

export const FacilitySummarySchema = z.object({
  count: z.number().int().nonnegative(),
  activeSlotsToday: z.number().int().nonnegative(),
});

export const EventSummarySchema = z.object({
  scheduled: z.number().int().nonnegative(),
  open: z.number().int().nonnegative(),
  closed: z.number().int().nonnegative(),
  totalSeats: z.number().int().nonnegative(),
  soldSeats: z.number().int().nonnegative(),
});

export const ProductSummarySchema = z.object({
  active: z.number().int().nonnegative(),
  outOfStock: z.number().int().nonnegative(),
});

export const DashboardSummarySchema = z.object({
  facilities: FacilitySummarySchema.nullable(),
  events: EventSummarySchema.nullable(),
  products: ProductSummarySchema.nullable(),
});
