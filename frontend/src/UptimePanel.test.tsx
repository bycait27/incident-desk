import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchUptimeMonitors } from './services/api';
import type { UptimeMonitorResponse } from './types';
import UptimePanel from './UptimePanel';

vi.mock('./services/api', () => ({
  fetchUptimeMonitors: vi.fn(),
}));

const fetchUptimeMonitorsMock = vi.mocked(fetchUptimeMonitors);
const emptyResponse: UptimeMonitorResponse = {
  available: true,
  stale: false,
  observedAt: '2025-01-02T03:04:05Z',
  message: null,
  monitors: [],
};

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('external monitoring fetch states', () => {
  it('shows loading while the request is pending, then the empty state', async () => {
    let resolveFetch!: (response: UptimeMonitorResponse) => void;
    fetchUptimeMonitorsMock.mockImplementation(
      () =>
        new Promise<UptimeMonitorResponse>((resolve) => {
          resolveFetch = resolve;
        }),
    );

    render(<UptimePanel />);

    expect(screen.getByText('Loading external monitoring status...')).toBeInTheDocument();

    await act(async () => {
      resolveFetch(emptyResponse);
    });

    expect(screen.getByText('No external monitors are configured.')).toBeInTheDocument();
  });

  it('shows a transport error and can retry successfully', async () => {
    fetchUptimeMonitorsMock
      .mockRejectedValueOnce(new Error('Forced test outage'))
      .mockResolvedValueOnce(emptyResponse);

    render(<UptimePanel />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Forced test outage');
    fireEvent.click(screen.getByRole('button', { name: 'Retry monitoring status' }));

    expect(await screen.findByText('No external monitors are configured.')).toBeInTheDocument();
    expect(fetchUptimeMonitorsMock).toHaveBeenCalledTimes(2);
  });

  it('shows a graceful provider-down message when the backend returns degraded data', async () => {
    fetchUptimeMonitorsMock.mockResolvedValue({
      available: false,
      stale: false,
      observedAt: null,
      message: 'Monitoring data is temporarily unavailable.',
      monitors: [],
    });

    render(<UptimePanel />);

    expect(
      await screen.findByText('Monitoring data is temporarily unavailable.'),
    ).toBeInTheDocument();
  });
});
