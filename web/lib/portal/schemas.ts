/**
 * B2B Portal zod 스키마.
 * BE Response DTO 검증 및 입력 검증에 사용한다.
 */
import { z } from "zod";

// ─── 공통 ───────────────────────────────────────────────────────────────────

export const PageSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z.object({
    content: z.array(itemSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  });

// ─── Facility ────────────────────────────────────────────────────────────────

const FacilityTypeSchema = z.enum(["INDOOR", "OUTDOOR", "MIXED"]);

export const MyFacilitySchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  gu: z.string(),
  type: FacilityTypeSchema,
  address: z.string(),
  location: z.string(),
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
  ownerId: z.number().int(),
  createdAt: z.string(),
  updatedAt: z.string(),
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

// ─── Dashboard ───────────────────────────────────────────────────────────────

export const FacilitySummarySchema = z.object({
  totalFacilities: z.number().int().nonnegative(),
  totalSlots: z.number().int().nonnegative(),
});

export const EventSummarySchema = z.object({
  totalEvents: z.number().int().nonnegative(),
  scheduledEvents: z.number().int().nonnegative(),
  openEvents: z.number().int().nonnegative(),
});

export const ProductSummarySchema = z.object({
  totalProducts: z.number().int().nonnegative(),
  activeProducts: z.number().int().nonnegative(),
  outOfStockProducts: z.number().int().nonnegative(),
});

export const DashboardSummarySchema = z.object({
  facilities: FacilitySummarySchema.nullable(),
  events: EventSummarySchema.nullable(),
  products: ProductSummarySchema.nullable(),
});
