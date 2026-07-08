"use client";

import * as React from "react";
import { facilityFormSchema, FacilityTypeEnum } from "@/app/portal/facilities/facility-form-schema";
import type { FacilityFormValues, FacilityTypeValue } from "@/app/portal/facilities/facility-form-schema";

export interface FormErrors {
  code?: string;
  name?: string;
  sido?: string;
  gu?: string;
  type?: string;
  address?: string;
  location?: string;
  parking?: string;
  tel?: string;
  homePage?: string;
  eduYn?: string;
  meta?: string;
}

export interface UseFacilityFormOptions {
  defaultValues?: Partial<FacilityFormValues>;
}

export interface UseFacilityFormReturn {
  values: FacilityFormValues;
  errors: FormErrors;
  handleChange: (field: keyof FacilityFormValues, value: string | boolean) => void;
  validate: () => boolean;
  reset: () => void;
}

const INITIAL_VALUES: FacilityFormValues = {
  code: "",
  name: "",
  sido: "",
  gu: "",
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
  type: "INDOOR" as FacilityTypeValue,
  address: "",
  location: "",
  parking: false,
  tel: "",
  homePage: "",
  eduYn: false,
  meta: "",
};

export function useFacilityForm(options: UseFacilityFormOptions = {}): UseFacilityFormReturn {
  const [values, setValues] = React.useState<FacilityFormValues>({
    ...INITIAL_VALUES,
    ...options.defaultValues,
  });
  const [errors, setErrors] = React.useState<FormErrors>({});

  const handleChange = React.useCallback(
    (field: keyof FacilityFormValues, value: string | boolean) => {
      setValues((prev) => ({ ...prev, [field]: value }));
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    },
    []
  );

  const validate = React.useCallback((): boolean => {
    const result = facilityFormSchema.safeParse(values);
    if (result.success) {
      setErrors({});
      return true;
    }
    const fieldErrors: FormErrors = {};
    for (const issue of result.error.issues) {
      const path = issue.path[0];
      if (typeof path === "string" && path in INITIAL_VALUES) {
        const key = path as keyof FormErrors;
        if (!fieldErrors[key]) {
          fieldErrors[key] = issue.message;
        }
      }
    }
    setErrors(fieldErrors);
    return false;
  }, [values]);

  const reset = React.useCallback(() => {
    setValues({ ...INITIAL_VALUES, ...options.defaultValues });
    setErrors({});
  }, [options.defaultValues]);

  return { values, errors, handleChange, validate, reset };
}

export { FacilityTypeEnum };
