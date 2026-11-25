import { describe, expect, it } from 'vitest';
import type { Incident } from './types';
import { summarizeIncidents } from './incidentSummary';

const incident: Incident = {
  id: 1,
  title: 'Synthetic incident',
  description: 'Synthetic description',
  status: 'OPEN',
  severity: 'LOW',
  published: true,
  archived: false,
  createdAt: '2026-11-24T00:00:00Z',
  updatedAt: '2026-11-24T00:00:00Z',
};

describe('incident summary', () => {
  it('counts visible and active incidents without counting archived incidents', () => {
    expect(summarizeIncidents([
      incident,
      { ...incident, id: 2 },
      { ...incident, id: 3, status: 'IN_PROGRESS' },
      { ...incident, id: 4, status: 'RESOLVED' },
      { ...incident, id: 5, status: 'CLOSED' },
      { ...incident, id: 6, archived: true },
    ])).toEqual({ visible: 5, active: 3 });
  });

  it('returns zero for an empty list', () => {
    expect(summarizeIncidents([])).toEqual({ visible: 0, active: 0 });
  });

  it('matches the hand prediction for a mix of statuses and an archived incident', () => {
    expect(summarizeIncidents([
      incident,
      { ...incident, id: 2 },
      { ...incident, id: 3, status: 'IN_PROGRESS' },
      { ...incident, id: 4, status: 'IN_PROGRESS' },
      { ...incident, id: 5, status: 'RESOLVED' },
      { ...incident, id: 6, status: 'RESOLVED' },
      { ...incident, id: 7, archived: true },
    ])).toEqual({ visible: 6, active: 4 });
  });
});
