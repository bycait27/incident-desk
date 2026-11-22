import { describe, expect, it } from 'vitest';
import type { Incident } from './types';
import { parseIncidentEvent, upsertIncident } from './incidentLiveUpdates';

const incident: Incident = {
  id: 9,
  title: 'Synthetic outage',
  description: 'SSE test incident.',
  status: 'OPEN',
  severity: 'LOW',
  createdAt: '2026-11-22T19:00:00Z',
  updatedAt: '2026-11-22T19:00:00Z',
};

describe('incident live-update handling', () => {
  it('accepts a valid event after the current cursor', () => {
    const result = parseIncidentEvent(
      '13',
      JSON.stringify({ eventType: 'CREATED', incident }),
      '12',
    );

    expect(result).toEqual({
      eventId: '13',
      payload: { eventType: 'CREATED', incident },
    });
  });

  it('ignores repeated or out-of-order event IDs', () => {
    const data = JSON.stringify({ eventType: 'CREATED', incident });

    expect(parseIncidentEvent('13', data, '13')).toBeNull();
    expect(parseIncidentEvent('12', data, '13')).toBeNull();
  });

  it('rejects malformed event data instead of silently dropping it', () => {
    expect(() => parseIncidentEvent('13', '{', '12')).toThrow('not valid JSON');
    expect(() => parseIncidentEvent('bad-id', '{}', '12')).toThrow('invalid ID');
  });

  it('upserts an incident without creating a duplicate row', () => {
    const changedIncident = { ...incident, status: 'RESOLVED' as const };

    expect(upsertIncident([incident], changedIncident)).toEqual([changedIncident]);
    expect(upsertIncident([], incident)).toEqual([incident]);
  });
});
