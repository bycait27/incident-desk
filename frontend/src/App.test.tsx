import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Incident } from './types';
import { fetchIncidents } from './services/api';
import App from './App';

vi.mock('./services/api', () => ({
  fetchIncidents: vi.fn(),
}));

const fetchIncidentsMock = vi.mocked(fetchIncidents);

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('incident fetch states', () => {
  it('shows loading while the request is pending, then the empty state', async () => {
    let resolveFetch!: (incidents: Incident[]) => void;
    fetchIncidentsMock.mockImplementation(
      () =>
        new Promise<Incident[]>((resolve) => {
          resolveFetch = resolve;
        }),
    );

    render(<App />);

    expect(screen.getByText('Loading active incidents...')).toBeInTheDocument();

    await act(async () => {
      resolveFetch([]);
    });

    expect(screen.getByText('No matching incidents found')).toBeInTheDocument();
  });

  it('shows the request error and allows retry after a forced failure', async () => {
    fetchIncidentsMock
      .mockRejectedValueOnce(new Error('Forced test outage'))
      .mockResolvedValueOnce([]);

    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Failed to Load Incidents' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Forced test outage')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry Connection' }));

    expect(screen.getByText('Loading active incidents...')).toBeInTheDocument();
    expect(await screen.findByText('No matching incidents found')).toBeInTheDocument();
    expect(fetchIncidentsMock).toHaveBeenCalledTimes(2);
  });
});
