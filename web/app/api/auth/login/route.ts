import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { z, ZodError } from "zod";
import { zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

const LoginSchema = z.object({
  email: z.string().min(1),
  password: z.string().min(1),
});

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
}

async function callBeLogin(
  email: string,
  password: string
): Promise<{ response: Response } | { error: NextResponse }> {
  const backendUrl = process.env["BACKEND_URL"];
  if (!backendUrl) {
    return {
      error: NextResponse.json(
        { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
        { status: 503 }
      ),
    };
  }

  let beResponse: Response;
  try {
    beResponse = await fetch(`${backendUrl}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
  } catch {
    return {
      error: NextResponse.json(
        { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
        { status: 503 }
      ),
    };
  }

  return { response: beResponse };
}

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "잘못된 요청입니다. 입력 값을 확인해 주세요." }, { status: 400 });
  }

  let parsed: z.infer<typeof LoginSchema>;
  try {
    parsed = LoginSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  const result = await callBeLogin(parsed.email, parsed.password);
  if ("error" in result) return result.error;

  const { response: beResponse } = result;
  const status = beResponse.status;

  if (status >= 500) {
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." },
      { status: 500 }
    );
  }

  if (status === 401) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 });
  }

  if (status >= 400) {
    return NextResponse.json(
      { message: "이메일 또는 비밀번호가 올바르지 않습니다." },
      { status }
    );
  }

  // 2xx — 토큰을 쿠키에 set하고 200 반환
  const loginData = (await beResponse.json()) as LoginResponse;

  const cookieStore = cookies();
  const cookieOptions = {
    httpOnly: true,
    secure: process.env["NODE_ENV"] === "production",
    sameSite: "lax" as const,
    path: "/",
  };

  cookieStore.set("access_token", loginData.accessToken, cookieOptions);
  cookieStore.set("refresh_token", loginData.refreshToken, cookieOptions);

  return NextResponse.json({ ok: true }, { status: 200 });
}
