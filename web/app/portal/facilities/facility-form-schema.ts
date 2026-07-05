/**
 * 시설 등록/수정 폼 클라이언트 검증 스키마.
 * server-only 의존이 없어 Client Component에서 직접 import 가능.
 */
import { z } from "zod";

export const FacilityTypeEnum = ["INDOOR", "OUTDOOR", "MIXED"] as const;
export type FacilityTypeValue = (typeof FacilityTypeEnum)[number];

export const facilityFormSchema = z.object({
  code: z.string().min(1, "시설 코드를 입력해 주세요."),
  name: z.string().min(1, "시설명을 입력해 주세요."),
  sido: z.string().optional(),
  gu: z.string().min(1, "구 정보를 입력해 주세요."),
  type: z.enum(FacilityTypeEnum, { message: "시설 유형을 선택해 주세요." }),
  address: z.string().min(1, "주소를 입력해 주세요."),
  location: z.string().min(1, "위치 좌표를 입력해 주세요."),
  parking: z.boolean(),
  tel: z.string().min(1, "전화번호를 입력해 주세요."),
  homePage: z.string().optional(),
  eduYn: z.boolean(),
  meta: z.string().optional(),
});

export type FacilityFormValues = z.infer<typeof facilityFormSchema>;

export const facilityUpdateFormSchema = facilityFormSchema
  .partial()
  .omit({ code: true })
  .refine((data) => Object.keys(data).length > 0, {
    message: "수정할 필드가 최소 1개 이상 있어야 합니다.",
  });

export type FacilityUpdateFormValues = z.infer<typeof facilityUpdateFormSchema>;
