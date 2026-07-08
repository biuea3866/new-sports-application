"use client";

/**
 * FacilityTabs — 시설 상세 탭(정보/운영시간/휴무일/시설상품).
 * design-fe-web.md 컴포넌트 트리: OperatingHoursForm·HolidaySection·ProgramSection을 조립한다.
 */
import * as React from "react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { OperatingHoursForm } from "./OperatingHoursForm";
import { HolidaySection } from "./HolidaySection";
import { ProgramSection } from "./ProgramSection";

export interface FacilityTabsProps {
  facilityId: string;
  infoContent: React.ReactNode;
}

export function FacilityTabs({ facilityId, infoContent }: FacilityTabsProps) {
  return (
    <Tabs defaultValue="info" aria-label="시설 상세 탭">
      <TabsList>
        <TabsTrigger value="info">정보</TabsTrigger>
        <TabsTrigger value="operating-hours">운영시간</TabsTrigger>
        <TabsTrigger value="holidays">휴무일</TabsTrigger>
        <TabsTrigger value="programs">시설상품</TabsTrigger>
      </TabsList>
      <TabsContent value="info">{infoContent}</TabsContent>
      <TabsContent value="operating-hours">
        <OperatingHoursForm facilityId={facilityId} />
      </TabsContent>
      <TabsContent value="holidays">
        <HolidaySection facilityId={facilityId} />
      </TabsContent>
      <TabsContent value="programs">
        <ProgramSection facilityId={facilityId} />
      </TabsContent>
    </Tabs>
  );
}
