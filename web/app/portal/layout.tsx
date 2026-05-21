"use client";

import { ToastProvider } from "@/components/ui/toast";

interface PortalLayoutProps {
  children: React.ReactNode;
}

export default function PortalLayout({ children }: PortalLayoutProps) {
  return <ToastProvider>{children}</ToastProvider>;
}
