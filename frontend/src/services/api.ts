import type { CurrentUser, Incident, IncidentSnapshot, UptimeMonitorResponse } from '../types';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export async function fetchIncidents(): Promise<Incident[]> {
  return (await fetchIncidentSnapshot()).incidents;
}

export async function fetchIncidentSnapshot(): Promise<IncidentSnapshot> {
  const response = await fetch(`${API_BASE_URL}/incidents`, { credentials: 'include' });
  if (!response.ok) {
    throw new Error(`Failed to fetch incidents: ${response.statusText}`);
  }
  const cursorHeader = response.headers.get('X-Incident-Event-Cursor');
  if (!cursorHeader || !/^\d+$/.test(cursorHeader)) {
    throw new Error('The backend did not return a valid incident event cursor.');
  }
  return { incidents: await response.json(), eventCursor: cursorHeader };
}

export async function fetchUptimeMonitors(): Promise<UptimeMonitorResponse> {
  const response = await fetch(`${API_BASE_URL}/uptime/monitors`);
  if (!response.ok) {
    throw new Error(`Failed to fetch monitoring status: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const response = await fetch(`${API_BASE_URL}/auth/me`, { credentials: 'include' });
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to check sign-in status: ${response.statusText}`);
  }
  return response.json();
}

async function getCsrfToken(): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' });
  if (!response.ok) {
    throw new Error(`Failed to get write authorization token: ${response.statusText}`);
  }
  const body: unknown = await response.json();
  if (typeof body !== 'object' || body === null || !('token' in body) || typeof body.token !== 'string') {
    throw new Error('The backend did not return a valid write authorization token.');
  }
  return body.token;
}

async function writeHeaders(): Promise<HeadersInit> {
  return {
    'Content-Type': 'application/json',
    'X-CSRF-TOKEN': await getCsrfToken(),
  };
}

async function writeError(response: Response, action: string): Promise<Error> {
  let detail = response.statusText;
  try {
    const body: unknown = await response.json();
    if (typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string') {
      detail = body.message;
    }
  } catch {
    // Keep the HTTP status text when the server does not return JSON.
  }
  return new Error(`${action} failed (${response.status}): ${detail}`);
}

export async function createIncident(incident: Pick<Incident, 'title' | 'description'>): Promise<Incident> {
  const response = await fetch(`${API_BASE_URL}/incidents`, {
    method: 'POST',
    credentials: 'include',
    headers: await writeHeaders(),
    body: JSON.stringify(incident),
  });
  if (!response.ok) {
    throw await writeError(response, 'Submitting incident');
  }
  return response.json();
}

export async function updateIncident(
  id: number,
  incident: Pick<Incident, 'title' | 'description' | 'status' | 'severity' | 'assignedTo'>,
): Promise<Incident> {
  const response = await fetch(`${API_BASE_URL}/incidents/${id}`, {
    method: 'PUT',
    credentials: 'include',
    headers: await writeHeaders(),
    body: JSON.stringify(incident),
  });
  if (!response.ok) {
    throw await writeError(response, 'Updating incident');
  }
  return response.json();
}

export async function publishIncident(id: number): Promise<Incident> {
  const response = await fetch(`${API_BASE_URL}/incidents/${id}/publish`, {
    method: 'POST',
    credentials: 'include',
    headers: await writeHeaders(),
  });
  if (!response.ok) {
    throw await writeError(response, 'Publishing incident');
  }
  return response.json();
}

export async function archiveIncident(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/incidents/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: await writeHeaders(),
  });
  if (!response.ok) {
    throw await writeError(response, 'Archiving incident');
  }
}