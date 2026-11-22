import { useState, type FormEvent } from 'react';

export interface IncidentSubmission {
  title: string;
  description: string;
}

interface IncidentSubmissionFormProps {
  onSubmit: (submission: IncidentSubmission) => Promise<void>;
}

export default function IncidentSubmissionForm({ onSubmit }: IncidentSubmissionFormProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ title, description });
      setTitle('');
      setDescription('');
    } catch (submitError: unknown) {
      setError(submitError instanceof Error ? submitError.message : 'Incident submission failed.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      aria-label="Submit an incident"
      onSubmit={handleSubmit}
      className="space-y-3 rounded-xl border border-slate-800 bg-slate-900 p-5"
    >
      <div>
        <h2 className="text-lg font-semibold text-white">Report an incident</h2>
        <p className="text-sm text-slate-400">
          Reports are private to IT until reviewed and published. Use synthetic details only.
        </p>
      </div>
      <label className="block text-sm text-slate-300">
        Title
        <input
          required
          maxLength={255}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
        />
      </label>
      <label className="block text-sm text-slate-300">
        Description
        <textarea
          required
          maxLength={4000}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          rows={3}
          className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
        />
      </label>
      {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
      <button
        type="submit"
        disabled={submitting}
        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-50"
      >
        {submitting ? 'Submitting…' : 'Submit for IT review'}
      </button>
    </form>
  );
}
