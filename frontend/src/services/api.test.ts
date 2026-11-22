import { afterEach, describe, expect, it, vi } from 'vitest';
import { createIncident, fetchCurrentUser } from './api';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('authenticated incident API calls', () => {
  it('treats an anonymous current-user response as signed out', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(null, { status: 401, statusText: 'Unauthorized' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchCurrentUser()).resolves.toBeNull();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/auth/me',
      { credentials: 'include' },
    );
  });

  it('submits with the session cookie and CSRF token from the backend', async () => {
    const createdIncident = {
      id: 21,
      title: 'Synthetic report',
      description: 'For the API test.',
      status: 'OPEN',
      severity: 'LOW',
      published: false,
      archived: false,
      createdAt: '2026-11-22T19:00:00Z',
      updatedAt: '2026-11-22T19:00:00Z',
    };
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'synthetic-csrf-token' })))
      .mockResolvedValueOnce(new Response(JSON.stringify(createdIncident), { status: 201 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(createIncident({
      title: 'Synthetic report',
      description: 'For the API test.',
    })).resolves.toEqual(createdIncident);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/auth/csrf',
      { credentials: 'include' },
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/incidents',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': 'synthetic-csrf-token',
        },
        body: JSON.stringify({
          title: 'Synthetic report',
          description: 'For the API test.',
        }),
      }),
    );
  });
});
