import { useState, type FormEvent } from 'react';
import type { Incident, IncidentSeverity, IncidentStatus } from './types';

interface OperatorIncidentControlsProps {
  incident: Incident;
  onSave: (incident: Pick<Incident, 'title' | 'description' | 'status' | 'severity' | 'assignedTo'>) => Promise<void>;
  onPublish: () => Promise<void>;
  onArchive: () => Promise<void>;
}

export default function OperatorIncidentControls({
  incident,
  onSave,
  onPublish,
  onArchive,
}: OperatorIncidentControlsProps) {
  const [title, setTitle] = useState(incident.title);
  const [description, setDescription] = useState(incident.description);
  const [status, setStatus] = useState<IncidentStatus>(incident.status);
  const [severity, setSeverity] = useState<IncidentSeverity>(incident.severity);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function perform(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (actionError: unknown) {
      setError(actionError instanceof Error ? actionError.message : 'Incident action failed.');
    } finally {
      setBusy(false);
    }
  }

  function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void perform(() => onSave({ title, description, status, severity, assignedTo: incident.assignedTo ?? null }));
  }

  return (
    <section aria-label={`IT controls for incident ${incident.id}`} className="space-y-3 border-t border-slate-800 pt-4">
      {!incident.published && (
        <p className="text-xs font-semibold uppercase tracking-wide text-amber-300">Private · awaiting review</p>
      )}
      <form
        aria-label={`Update incident ${incident.id}`}
        onSubmit={handleSave}
        className="grid gap-3 md:grid-cols-2"
      >
        <label className="text-sm text-slate-300">
          Edit title
          <input
            required
            maxLength={255}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          />
        </label>
        <label className="text-sm text-slate-300">
          Status
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as IncidentStatus)}
            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          >
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>
        </label>
        <label className="text-sm text-slate-300 md:col-span-2">
          Edit description
          <textarea
            required
            maxLength={4000}
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          />
        </label>
        <label className="text-sm text-slate-300">
          Severity
          <select
            value={severity}
            onChange={(event) => setSeverity(event.target.value as IncidentSeverity)}
            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          >
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </select>
        </label>
        <div className="flex flex-wrap items-end gap-2">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-slate-700 px-3 py-2 text-sm text-white hover:bg-slate-600 disabled:opacity-50"
          >
            Save changes
          </button>
          {!incident.published && (
            <button
              type="button"
              disabled={busy}
              onClick={() => void perform(onPublish)}
              className="rounded-lg bg-emerald-700 px-3 py-2 text-sm text-white hover:bg-emerald-600 disabled:opacity-50"
            >
              Publish
            </button>
          )}
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              if (window.confirm('Archive this incident? It will disappear from active views, but its audit history will remain.')) {
                void perform(onArchive);
              }
            }}
            className="rounded-lg bg-red-900 px-3 py-2 text-sm text-white hover:bg-red-800 disabled:opacity-50"
          >
            Archive
          </button>
        </div>
      </form>
      {busy && <p role="status" className="text-sm text-slate-400">Processing incident change…</p>}
      {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
    </section>
  );
}
