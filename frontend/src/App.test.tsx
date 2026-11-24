import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser, Incident, IncidentSnapshot, UptimeMonitorResponse } from './types';
import {
  archiveIncident,
  createIncident,
  fetchCurrentUser,
  fetchIncidentSnapshot,
  fetchUptimeMonitors,
  logout,
  publishIncident,
  updateIncident,
} from './services/api';
import App from './App';

vi.mock('./services/api', () => ({
  API_BASE_URL: 'http://localhost:8080/api',
  archiveIncident: vi.fn(),
  createIncident: vi.fn(),
  fetchCurrentUser: vi.fn(),
  fetchIncidentSnapshot: vi.fn(),
  fetchUptimeMonitors: vi.fn(),
  logout: vi.fn(),
  publishIncident: vi.fn(),
  updateIncident: vi.fn(),
}));

const fetchCurrentUserMock = vi.mocked(fetchCurrentUser);
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
  closed = false;
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
    this.closed = true;
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
    fetchCurrentUserMock.mockResolvedValue(null);
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

  it('lets a signed-in user sign out', async () => {
    fetchCurrentUserMock.mockResolvedValue({
      subject: 'viewer-subject',
      email: 'viewer@example.test',
      roles: ['ROLE_OPERATOR'],
    });
    const publicIncident: Incident = {
      id: 21,
      title: 'Published synthetic incident',
      description: 'Visible to everyone.',
      status: 'OPEN',
      severity: 'LOW',
      published: true,
      archived: false,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    const privateIncident: Incident = {
      ...publicIncident,
      id: 22,
      title: 'Private synthetic incident',
      published: false,
    };
    fetchIncidentSnapshotMock
      .mockResolvedValueOnce({ incidents: [publicIncident, privateIncident], eventCursor: '2' })
      .mockResolvedValueOnce({ incidents: [publicIncident], eventCursor: '2' });
    vi.mocked(logout).mockResolvedValue(undefined);

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(await screen.findByText('You have signed out.')).toBeInTheDocument();
    expect(screen.queryByText('Signed in as viewer@example.test · IT operator')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
    expect(screen.queryByText('Private synthetic incident')).not.toBeInTheDocument();
    expect(screen.getByText('Published synthetic incident')).toBeInTheDocument();
    expect(logout).toHaveBeenCalledOnce();
    expect(fetchIncidentSnapshotMock).toHaveBeenCalledTimes(2);
    expect(FakeEventSource.instances.length).toBeGreaterThan(1);
    expect(FakeEventSource.instances.slice(0, -1).every((source) => source.closed)).toBe(true);
    expect(FakeEventSource.instances[FakeEventSource.instances.length - 1]?.closed).toBe(false);
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
      published: true,
      archived: false,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    const createdIncident: Incident = {
      ...initialIncident,
      id: 9,
      title: 'New synthetic incident',
      description: 'Published over SSE',
      published: true,
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

  it('lets a signed-in viewer submit a private incident without exposing it on the public board', async () => {
    const viewer: CurrentUser = {
      subject: 'viewer-subject',
      email: 'viewer@example.test',
      roles: ['ROLE_VIEWER'],
    };
    const privateIncident: Incident = {
      id: 21,
      title: 'Private synthetic report',
      description: 'Awaiting IT review',
      status: 'OPEN',
      severity: 'LOW',
      published: false,
      archived: false,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    fetchCurrentUserMock.mockResolvedValue(viewer);
    fetchIncidentSnapshotMock.mockResolvedValue({ incidents: [], eventCursor: '0' });
    vi.mocked(createIncident).mockResolvedValue(privateIncident);

    render(<App />);

    const form = await screen.findByRole('form', { name: 'Submit an incident' });
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: privateIncident.title } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: privateIncident.description } });
    fireEvent.submit(form);

    expect(await screen.findByText('Your report was submitted privately for IT review.')).toBeInTheDocument();
    expect(screen.queryByText('Private synthetic report')).not.toBeInTheDocument();
    expect(createIncident).toHaveBeenCalledWith({
      title: 'Private synthetic report',
      description: 'Awaiting IT review',
    });
  });

  it('shows operator publish and archive controls for unpublished synthetic reports', async () => {
    const operator: CurrentUser = {
      subject: 'operator-subject',
      email: 'operator@example.test',
      roles: ['ROLE_OPERATOR'],
    };
    const pendingIncident: Incident = {
      id: 22,
      title: 'Awaiting review',
      description: 'Synthetic report for moderation.',
      status: 'OPEN',
      severity: 'LOW',
      published: false,
      archived: false,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    fetchCurrentUserMock.mockResolvedValue(operator);
    fetchIncidentSnapshotMock.mockResolvedValue({ incidents: [pendingIncident], eventCursor: '0' });
    vi.mocked(publishIncident).mockResolvedValue({ ...pendingIncident, published: true });
    vi.mocked(updateIncident).mockResolvedValue({ ...pendingIncident, title: 'Edited incident' });
    vi.mocked(archiveIncident).mockResolvedValue(undefined);
    vi.stubGlobal('confirm', vi.fn(() => true));

    render(<App />);

    expect(await screen.findByText('Awaiting review')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Edit title'), { target: { value: 'Edited incident' } });
    fireEvent.submit(screen.getByRole('form', { name: 'Update incident 22' }));
    await waitFor(() => {
      expect(updateIncident).toHaveBeenCalledWith(22, expect.objectContaining({ title: 'Edited incident' }));
    });

    fireEvent.click(screen.getByRole('button', { name: 'Publish' }));

    await waitFor(() => {
      expect(publishIncident).toHaveBeenCalledWith(22);
    });
    expect(await screen.findByText('Incident INC-22 is now public.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }));
    await waitFor(() => {
      expect(archiveIncident).toHaveBeenCalledWith(22);
    });
    expect(await screen.findByText('Incident INC-22 was archived; its audit history was retained.')).toBeInTheDocument();
    expect(screen.queryByText('Edited incident')).not.toBeInTheDocument();
  });
});
