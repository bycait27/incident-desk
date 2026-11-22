export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Incident {
  id: number;
  title: string;
  description: string;
  status: IncidentStatus;
  severity: IncidentSeverity;
  reporterId?: number | null;
  assignedTo?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface UptimeMonitor {
  id: string;
  name: string;
  url: string;
  status: string;
  lastCheckedAt: string | null;
}

export interface UptimeMonitorResponse {
  available: boolean;
  stale: boolean;
  observedAt: string | null;
  message: string | null;
  monitors: UptimeMonitor[];
}

export interface IncidentSnapshot {
  incidents: Incident[];
  eventCursor: string;
}

export interface IncidentEventPayload {
  eventType: 'CREATED' | 'UPDATED';
  incident: Incident;
}