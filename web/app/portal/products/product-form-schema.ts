/**
 * 상품 등록/수정 폼 클라이언트 검증 스키마.
 * server-only 의존이 없어 Client Component에서 직접 import 가능.
 */
import { z } from "zod";

export const productFormSchema = z.object({
  name: z.string().min(1, "상품명을 입력해 주세요."),
  description: z.string().min(1, "상품 설명을 입력해 주세요."),
  price: z
    .number({ invalid_type_error: "가격을 입력해 주세요." })
    .int("가격은 정수여야 합니다.")
    .positive("가격은 0보다 커야 합니다."),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;

export const productUpdateFormSchema = productFormSchema
  .partial()
  .refine((data) => Object.keys(data).some((k) => data[k as keyof typeof data] !== undefined), {
    message: "수정할 필드가 최소 1개 이상 있어야 합니다.",
  });

export type ProductUpdateFormValues = z.infer<typeof productUpdateFormSchema>;

export const restoreStockFormSchema = z.object({
  quantity: z
    .number({ invalid_type_error: "수량을 입력해 주세요." })
    .int("수량은 정수여야 합니다.")
    .positive("재고 수량은 1 이상이어야 합니다."),
});

export type RestoreStockFormValues = z.infer<typeof restoreStockFormSchema>;
