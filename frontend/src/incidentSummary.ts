import type { Incident } from './types';

export function summarizeIncidents(incidents: Incident[]): { visible: number; active: number } {
  return incidents.reduce(
    (summary, incident) => {
      if (!incident.archived) {
        summary.visible += 1;
        if (incident.status === 'OPEN' || incident.status === 'IN_PROGRESS') {
          summary.active += 1;
        }
      }
      return summary;
    },
    { visible: 0, active: 0 },
  );
}
