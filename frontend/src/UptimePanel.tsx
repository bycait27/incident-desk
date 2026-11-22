import { useEffect, useState } from 'react';
import { fetchUptimeMonitors } from './services/api';
import type { UptimeMonitorResponse } from './types';

function getErrorMessage(error: unknown): string {
  return error instanceof Error && error.message
    ? error.message
    : 'An unexpected error occurred while loading monitoring status.';
}

export default function UptimePanel() {
  const [response, setResponse] = useState<UptimeMonitorResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let active = true;

    fetchUptimeMonitors()
      .then((data) => {
        if (active) {
          setResponse(data);
        }
      })
      .catch((requestError: unknown) => {
        if (active) {
          setError(getErrorMessage(requestError));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [retryCount]);

  const retry = () => {
    setLoading(true);
    setError(null);
    setRetryCount((count) => count + 1);
  };

  return (
    <section
      aria-labelledby="uptime-heading"
      className="rounded-xl border border-slate-800 bg-slate-900 p-5 space-y-3"
    >
      <div>
        <h2 id="uptime-heading" className="text-lg font-semibold text-white">
          External service monitoring
        </h2>
        <p className="text-xs text-slate-500">Status provided by Better Stack</p>
      </div>

      {loading && (
        <p role="status" className="text-sm text-slate-400">
          Loading external monitoring status...
        </p>
      )}

      {error && !loading && (
        <div role="alert" className="space-y-2 text-sm text-red-300">
          <p>Could not load external monitoring status: {error}</p>
          <button
            type="button"
            onClick={retry}
            className="rounded-lg border border-red-800 px-3 py-1.5 text-red-200 hover:bg-red-950"
          >
            Retry monitoring status
          </button>
        </div>
      )}

      {!loading && !error && response && !response.available && (
        <p role="status" className="text-sm text-amber-300">
          {response.message ?? 'External monitoring is temporarily unavailable.'}
        </p>
      )}

      {!loading && !error && response?.available && (
        <>
          {response.stale && (
            <p role="status" className="text-sm text-amber-300">
              {response.message}
              {response.observedAt && ` Last updated ${new Date(response.observedAt).toLocaleString()}.`}
            </p>
          )}

          {response.monitors.length === 0 ? (
            <p className="text-sm text-slate-400">No external monitors are configured.</p>
          ) : (
            <ul className="divide-y divide-slate-800">
              {response.monitors.map((monitor) => (
                <li
                  key={monitor.id}
                  className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <p className="text-sm font-medium text-slate-100">{monitor.name}</p>
                    <p className="text-xs text-slate-500">{monitor.url}</p>
                  </div>
                  <span
                    className={`w-fit rounded-full border px-2.5 py-1 text-xs font-medium uppercase ${
                      monitor.status === 'up'
                        ? 'border-emerald-800 bg-emerald-950 text-emerald-300'
                        : 'border-amber-800 bg-amber-950 text-amber-300'
                    }`}
                  >
                    {monitor.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
