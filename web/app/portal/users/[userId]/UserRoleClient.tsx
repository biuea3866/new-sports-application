"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

const ASSIGNABLE_ROLES = [
  "FACILITY_OWNER",
  "EVENT_HOST",
  "GOODS_SELLER",
  "ADMIN",
] as const;

interface UserRoleClientProps {
  userId: number;
  initialRoleNames: string[];
}

export default function UserRoleClient({ userId, initialRoleNames }: UserRoleClientProps) {
  const [roleNames, setRoleNames] = useState<string[]>(initialRoleNames);
  const [pendingRole, setPendingRole] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleAssign(roleName: string) {
    setPendingRole(roleName);
    setErrorMessage(null);

    try {
      const res = await fetch(`/api/portal/admin/users/${userId}/roles/${roleName}`, {
        method: "POST",
      });
      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setErrorMessage(body.message ?? "역할 부여에 실패했습니다.");
        return;
      }
      setRoleNames((prev) => [...prev, roleName]);
    } catch {
      setErrorMessage("네트워크 오류가 발생했습니다.");
    } finally {
      setPendingRole(null);
    }
  }

  async function handleRevoke(roleName: string) {
    setPendingRole(roleName);
    setErrorMessage(null);

    try {
      const res = await fetch(`/api/portal/admin/users/${userId}/roles/${roleName}`, {
        method: "DELETE",
      });
      if (!res.ok) {
        const body = (await res.json()) as { message?: string };
        setErrorMessage(body.message ?? "역할 회수에 실패했습니다.");
        return;
      }
      setRoleNames((prev) => prev.filter((r) => r !== roleName));
    } catch {
      setErrorMessage("네트워크 오류가 발생했습니다.");
    } finally {
      setPendingRole(null);
    }
  }

  return (
    <section aria-labelledby="roles-heading">
      <h2 id="roles-heading" className="text-lg font-semibold mb-3">
        역할 관리
      </h2>

      {errorMessage !== null && (
        <div
          role="alert"
          className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive mb-4"
        >
          {errorMessage}
        </div>
      )}

      <div className="rounded-md border overflow-hidden">
        <table className="w-full text-left" aria-label="역할 목록">
          <thead className="bg-muted/50">
            <tr>
              <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                역할
              </th>
              <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                상태
              </th>
              <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                액션
              </th>
            </tr>
          </thead>
          <tbody>
            {ASSIGNABLE_ROLES.map((roleName) => {
              const hasRole = roleNames.includes(roleName);
              const isActing = pendingRole === roleName;
              return (
                <tr key={roleName} className="border-t">
                  <td className="py-3 px-4 text-sm font-mono">{roleName}</td>
                  <td className="py-3 px-4">
                    {hasRole ? (
                      <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-800">
                        보유
                      </span>
                    ) : (
                      <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-600">
                        미보유
                      </span>
                    )}
                  </td>
                  <td className="py-3 px-4">
                    {hasRole ? (
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => void handleRevoke(roleName)}
                        disabled={isActing}
                        aria-label={`${roleName} 역할 회수`}
                        aria-busy={isActing}
                      >
                        {isActing ? "처리 중..." : "회수"}
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void handleAssign(roleName)}
                        disabled={isActing}
                        aria-label={`${roleName} 역할 부여`}
                        aria-busy={isActing}
                      >
                        {isActing ? "처리 중..." : "부여"}
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
