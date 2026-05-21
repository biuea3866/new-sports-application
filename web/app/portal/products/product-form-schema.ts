import { z } from "zod";

export const productFormSchema = z.object({
  name: z.string().min(1, "상품명을 입력해 주세요."),
  description: z.string().min(1, "상품 설명을 입력해 주세요."),
  price: z
    .number({ invalid_type_error: "가격을 입력해 주세요." })
    .int("가격은 정수여야 합니다.")
    .positive("가격은 0보다 커야 합니다."),
});

export const productUpdateFormSchema = productFormSchema.partial().refine(
  (data) => Object.values(data).some((v) => v !== undefined),
  { message: "하나 이상의 필드를 입력해 주세요." }
);

export const restoreStockFormSchema = z.object({
  quantity: z
    .number({ invalid_type_error: "수량을 입력해 주세요." })
    .int("수량은 정수여야 합니다.")
    .positive("재고 수량은 1 이상이어야 합니다."),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;
export type ProductUpdateFormValues = z.infer<typeof productUpdateFormSchema>;
export type RestoreStockFormValues = z.infer<typeof restoreStockFormSchema>;
