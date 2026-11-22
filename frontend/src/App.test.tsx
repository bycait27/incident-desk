import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Incident, IncidentSnapshot, UptimeMonitorResponse } from './types';
import { fetchIncidentSnapshot, fetchUptimeMonitors } from './services/api';
import App from './App';

vi.mock('./services/api', () => ({
  API_BASE_URL: 'http://localhost:8080/api',
  fetchIncidentSnapshot: vi.fn(),
  fetchUptimeMonitors: vi.fn(),
}));

const fetchIncidentSnapshotMock = vi.mocked(fetchIncidentSnapshot);
const fetchUptimeMonitorsMock = vi.mocked(fetchUptimeMonitors);
const emptyMonitoringResponse: UptimeMonitorResponse = {
  available: true,
  stale: false,
  observedAt: '2025-01-02T03:04:05Z',
  message: null,
  monitors: [],
};

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  private readonly listeners = new Map<string, EventListener[]>();
  readonly url: string;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: EventListener): void {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  close(): void {
    // The test double has no open network connection.
  }

  emitIncident(eventId: string, data: string): void {
    const event = new MessageEvent('incident', { data, lastEventId: eventId });
    this.listeners.get('incident')?.forEach((listener) => listener(event));
  }
}

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

describe('incident fetch states', () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    fetchUptimeMonitorsMock.mockResolvedValue(emptyMonitoringResponse);
  });

  it('shows loading while the request is pending, then the empty state', async () => {
    let resolveFetch!: (snapshot: IncidentSnapshot) => void;
    fetchIncidentSnapshotMock.mockImplementation(
      () =>
        new Promise<IncidentSnapshot>((resolve) => {
          resolveFetch = resolve;
        }),
    );

    render(<App />);

    expect(screen.getByText('Loading active incidents...')).toBeInTheDocument();

    await act(async () => {
      resolveFetch({ incidents: [], eventCursor: '0' });
    });

    expect(screen.getByText('No matching incidents found')).toBeInTheDocument();
  });

  it('shows the request error and allows retry after a forced failure', async () => {
    fetchIncidentSnapshotMock
      .mockRejectedValueOnce(new Error('Forced test outage'))
      .mockResolvedValueOnce({ incidents: [], eventCursor: '0' });

    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Failed to Load Incidents' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Forced test outage')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry Connection' }));

    expect(screen.getByText('Loading active incidents...')).toBeInTheDocument();
    expect(await screen.findByText('No matching incidents found')).toBeInTheDocument();
    expect(fetchIncidentSnapshotMock).toHaveBeenCalledTimes(2);
  });

  it('applies each new SSE incident once and reconnects from the snapshot cursor', async () => {
    const initialIncident: Incident = {
      id: 8,
      title: 'Initial synthetic incident',
      description: 'Existing state',
      status: 'OPEN',
      severity: 'LOW',
      reporterId: null,
      assignedTo: null,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    const createdIncident: Incident = {
      ...initialIncident,
      id: 9,
      title: 'New synthetic incident',
      description: 'Published over SSE',
      createdAt: '2026-11-22T19:01:00Z',
      updatedAt: '2026-11-22T19:01:00Z',
    };
    fetchIncidentSnapshotMock.mockResolvedValue({
      incidents: [initialIncident],
      eventCursor: '12',
    });

    render(<App />);

    expect(await screen.findByText('Initial synthetic incident')).toBeInTheDocument();
    expect(FakeEventSource.instances[0]?.url).toContain('after=12');

    const eventSource = FakeEventSource.instances[0];
    act(() => {
      eventSource?.onopen?.(new Event('open'));
    });
    expect(screen.getByText('Live updates: connected')).toBeInTheDocument();

    const payload = JSON.stringify({ eventType: 'CREATED', incident: createdIncident });
    act(() => {
      eventSource?.emitIncident('13', payload);
      eventSource?.emitIncident('13', payload);
    });

    expect(await screen.findByText('New synthetic incident')).toBeInTheDocument();
    expect(screen.getAllByText('New synthetic incident')).toHaveLength(1);
  });
});
