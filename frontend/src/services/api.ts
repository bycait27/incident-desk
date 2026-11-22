import type { Incident, UptimeMonitorResponse } from '../types';

const API_BASE_URL = 'http://localhost:8080/api';

export async function fetchIncidents(): Promise<Incident[]> {
  const response = await fetch(`${API_BASE_URL}/incidents`);
  if (!response.ok) {
    throw new Error(`Failed to fetch incidents: ${response.statusText}`);
  }
  return response.json();
}

export async function fetchUptimeMonitors(): Promise<UptimeMonitorResponse> {
  const response = await fetch(`${API_BASE_URL}/uptime/monitors`);
  if (!response.ok) {
    throw new Error(`Failed to fetch monitoring status: ${response.statusText}`);
  }
  return response.json();
}

export async function createIncident(incident: Omit<Incident, 'id' | 'createdAt' | 'updatedAt'>, actorEmail: string): Promise<Incident> {
  const response = await fetch(`${API_BASE_URL}/incidents`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Email': actorEmail,
    },
    body: JSON.stringify(incident),
  });
  if (!response.ok) {
    throw new Error('Failed to create incident');
  }
  return response.json();
}