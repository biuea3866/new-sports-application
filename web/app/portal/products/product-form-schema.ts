import { z } from "zod";

export const PRODUCT_CATEGORIES = ["EQUIPMENT", "APPAREL", "FOOTWEAR", "ACCESSORY"] as const;
export type ProductCategory = (typeof PRODUCT_CATEGORIES)[number];

export const productFormSchema = z.object({
  name: z.string().min(1, "상품명을 입력해 주세요."),
  description: z.string().min(1, "상품 설명을 입력해 주세요."),
  price: z
    .number({ message: "가격을 입력해 주세요." })
    .int("가격은 정수여야 합니다.")
    .positive("가격은 0보다 커야 합니다."),
  category: z.enum(PRODUCT_CATEGORIES, { message: "카테고리를 선택해 주세요." }),
  imageUrl: z.string().url("올바른 이미지 URL을 입력해 주세요.").min(1, "이미지 URL을 입력해 주세요."),
});

export const productUpdateFormSchema = z
  .object({
    name: z.string().min(1, "상품명을 입력해 주세요.").optional(),
    description: z.string().min(1, "상품 설명을 입력해 주세요.").optional(),
    price: z
      .number({ message: "가격을 입력해 주세요." })
      .int("가격은 정수여야 합니다.")
      .positive("가격은 0보다 커야 합니다.")
      .optional(),
    category: z
      .enum(PRODUCT_CATEGORIES, { message: "카테고리를 선택해 주세요." })
      .optional(),
    imageUrl: z.string().url("올바른 이미지 URL을 입력해 주세요.").optional(),
  })
  .refine((data) => Object.values(data).some((v) => v !== undefined), {
    message: "하나 이상의 필드를 입력해 주세요.",
  });

export const restoreStockFormSchema = z.object({
  quantity: z
    .number({ message: "수량을 입력해 주세요." })
    .int("수량은 정수여야 합니다.")
    .positive("재고 수량은 1 이상이어야 합니다."),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;
export type ProductUpdateFormValues = z.infer<typeof productUpdateFormSchema>;
export type RestoreStockFormValues = z.infer<typeof restoreStockFormSchema>;
