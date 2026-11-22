import type { Incident, IncidentEventPayload } from './types';

type IncidentWire = Omit<Incident, 'published' | 'archived'> &
  Partial<Pick<Incident, 'published' | 'archived'>>;

type IncidentEventType = IncidentEventPayload['eventType'];

function isIncidentEventType(value: unknown): value is IncidentEventType {
  return typeof value === 'string'
    && ['CREATED', 'UPDATED', 'PUBLISHED', 'ARCHIVED'].some((eventType) => eventType === value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isIncident(value: unknown): value is IncidentWire {
  if (!isRecord(value)) {
    return false;
  }
  return Number.isSafeInteger(value.id)
    && typeof value.title === 'string'
    && typeof value.description === 'string'
    && ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].includes(String(value.status))
    && ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(String(value.severity))
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
    && (value.published === undefined || typeof value.published === 'boolean')
    && (value.archived === undefined || typeof value.archived === 'boolean')
    && (value.reporterId === undefined || value.reporterId === null || typeof value.reporterId === 'number')
    && (value.assignedTo === undefined || value.assignedTo === null || typeof value.assignedTo === 'number');
}

export function parseIncidentEvent(
  eventId: string,
  data: string,
  lastSeenEventId: string,
): { eventId: string; payload: IncidentEventPayload } | null {
  if (!/^\d+$/.test(eventId)) {
    throw new Error('Incident event has an invalid ID.');
  }
  if (BigInt(eventId) <= BigInt(lastSeenEventId)) {
    return null;
  }

  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    throw new Error('Incident event body is not valid JSON.');
  }

  if (!isRecord(value)
    || !isIncidentEventType(value.eventType)
    || !isIncident(value.incident)) {
    throw new Error('Incident event body does not match the expected schema.');
  }

  const incident: Incident = {
    ...value.incident,
    published: value.incident.published ?? true,
    archived: value.incident.archived ?? false,
  };

  return {
    eventId,
    payload: {
      eventType: value.eventType,
      incident,
    },
  };
}

export function upsertIncident(incidents: Incident[], update: Incident): Incident[] {
  if (update.archived) {
    return incidents.filter((incident) => incident.id !== update.id);
  }
  const existingIndex = incidents.findIndex((incident) => incident.id === update.id);
  if (existingIndex < 0) {
    return [...incidents, update];
  }
  return incidents.map((incident) => incident.id === update.id ? update : incident);
}
