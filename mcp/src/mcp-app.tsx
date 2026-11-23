import { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { App as McpApp } from "@modelcontextprotocol/ext-apps";
import { z } from "zod";
import "./mcp-app.css";

const incidentSchema = z.object({
  id: z.number(),
  title: z.string(),
  description: z.string(),
  status: z.string(),
  severity: z.string(),
});
const incidentsResultSchema = z.object({ incidents: z.array(incidentSchema) });
const monitorResultSchema = z.object({
  monitorStatus: z.object({
    available: z.boolean(),
    stale: z.boolean(),
    observedAt: z.string().nullable(),
    message: z.string().nullable(),
    monitors: z.array(z.object({
      id: z.string(),
      name: z.string(),
      url: z.string(),
      status: z.string(),
      lastCheckedAt: z.string().nullable(),
    })),
  }),
});
const submittedIncidentSchema = z.object({ incident: incidentSchema });
type Incident = z.infer<typeof incidentSchema>;
type MonitorStatus = z.infer<typeof monitorResultSchema>["monitorStatus"];

function AppView() {
  const appRef = useRef<McpApp | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [incidentLoading, setIncidentLoading] = useState(true);
  const [incidentError, setIncidentError] = useState("");
  const [monitorStatus, setMonitorStatus] = useState<MonitorStatus | null>(null);
  const [monitorLoading, setMonitorLoading] = useState(true);
  const [monitorError, setMonitorError] = useState("");
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [submitted, setSubmitted] = useState<Incident | null>(null);

  async function loadIncidents(app: McpApp) {
    setIncidentLoading(true);
    setIncidentError("");
    try {
      const result = await app.callServerTool({ name: "list_incidents", arguments: {} });
      setIncidents(parseResult(result, incidentsResultSchema).incidents);
    } catch (error) {
      setIncidentError(errorMessage(error));
    } finally {
      setIncidentLoading(false);
    }
  }

  async function loadMonitors(app: McpApp) {
    setMonitorLoading(true);
    setMonitorError("");
    try {
      const result = await app.callServerTool({ name: "check_monitor_status", arguments: {} });
      setMonitorStatus(parseResult(result, monitorResultSchema).monitorStatus);
    } catch (error) {
      setMonitorError(errorMessage(error));
    } finally {
      setMonitorLoading(false);
    }
  }

  useEffect(() => {
    const app = new McpApp({ name: "CampusPulse Incident Desk", version: "1.0.0" });
    appRef.current = app;
    let active = true;
    void app.connect().then(async () => {
      if (!active) return;
      await Promise.all([loadIncidents(app), loadMonitors(app)]);
    }).catch((error: unknown) => {
      if (!active) return;
      setIncidentError(errorMessage(error));
      setMonitorError(errorMessage(error));
      setIncidentLoading(false);
      setMonitorLoading(false);
    });
    return () => {
      active = false;
      appRef.current = null;
      void app.close();
    };
  }, []);

  const visibleIncidents = incidents.filter((incident) => {
    const matchesText = `${incident.title} ${incident.description}`.toLowerCase().includes(query.toLowerCase());
    const matchesStatus = statusFilter === "ALL" || incident.status === statusFilter;
    return matchesText && matchesStatus;
  });

  async function openIncident(id: number) {
    const app = appRef.current;
    if (!app) return;
    setDetailLoading(true);
    setDetailError("");
    setSelectedIncident(null);
    try {
      const result = await app.callServerTool({ name: "get_incident", arguments: { id } });
      setSelectedIncident(parseResult(result, z.object({ incident: incidentSchema })).incident);
    } catch (error) {
      setDetailError(errorMessage(error));
    } finally {
      setDetailLoading(false);
    }
  }

  async function confirmSubmission() {
    const app = appRef.current;
    if (!app) return;
    setSubmitting(true);
    setSubmitError("");
    try {
      const result = await app.callServerTool({
        name: "submit_incident",
        arguments: { title, description },
      });
      setSubmitted(parseResult(result, submittedIncidentSchema).incident);
      setTitle("");
      setDescription("");
      setReviewing(false);
    } catch (error) {
      setSubmitError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <header className="topbar">
        <div className="brand-mark" aria-hidden="true">CP</div>
        <div>
          <p className="eyebrow">CAMPUS SERVICE STATUS</p>
          <h1>CampusPulse</h1>
        </div>
        <span className="live-badge"><span /> Live status</span>
      </header>

      <section className="status-card" aria-labelledby="status-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">MONITORING</p>
            <h2 id="status-title">Service health</h2>
          </div>
          <button className="text-button" onClick={() => appRef.current && void loadMonitors(appRef.current)}>
            Refresh
          </button>
        </div>
        {monitorLoading && <p role="status" className="state-message">Checking service monitors…</p>}
        {!monitorLoading && monitorError && <p role="alert" className="state-message error">{monitorError}</p>}
        {!monitorLoading && !monitorError && monitorStatus && (
          <>
            <p className="health-summary">
              <span className={`health-dot ${monitorStatus.available ? "healthy" : "unavailable"}`} />
              {monitorStatus.available ? "Monitoring data available" : "Monitoring data unavailable"}
              {monitorStatus.stale && <span className="stale-label">Stale data</span>}
            </p>
            {monitorStatus.message && <p className="muted">{monitorStatus.message}</p>}
            <ul className="monitor-list">
              {monitorStatus.monitors.map((monitor) => (
                <li key={monitor.id}>
                  <div><strong>{monitor.name}</strong><span>{monitor.url}</span></div>
                  <span className={`status-pill ${monitor.status.toLowerCase()}`}>{monitor.status}</span>
                </li>
              ))}
            </ul>
            {monitorStatus.monitors.length === 0 && <p className="muted">No monitors are configured.</p>}
          </>
        )}
      </section>

      <section className="incidents-section" aria-labelledby="incidents-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">CAMPUS UPDATES</p>
            <h2 id="incidents-title">Incident reports</h2>
          </div>
          <span className="count-badge">{incidents.length} reports</span>
        </div>

        <div className="filters">
          <label>
            <span className="sr-only">Search incidents</span>
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search reports" />
          </label>
          <label>
            <span className="sr-only">Filter by status</span>
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">All statuses</option>
              <option value="OPEN">Open</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </label>
          <button className="text-button" onClick={() => appRef.current && void loadIncidents(appRef.current)}>
            Refresh
          </button>
        </div>

        {incidentLoading && <p role="status" className="state-message">Loading incident reports…</p>}
        {!incidentLoading && incidentError && <p role="alert" className="state-message error">{incidentError}</p>}
        {!incidentLoading && !incidentError && visibleIncidents.length === 0 && (
          <p className="state-message">No reports match your search.</p>
        )}
        {!incidentLoading && !incidentError && visibleIncidents.length > 0 && (
          <ul className="incident-list">
            {visibleIncidents.map((incident) => (
              <li key={incident.id}>
                <button className="incident-row" onClick={() => void openIncident(incident.id)}>
                  <span className="incident-icon" aria-hidden="true">!</span>
                  <span className="incident-copy">
                    <strong>{incident.title}</strong>
                    <span>{incident.description}</span>
                  </span>
                  <span className={`status-pill ${incident.status.toLowerCase()}`}>{incident.status}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {detailLoading && <p role="status" className="state-message">Loading incident details…</p>}
        {detailError && <p role="alert" className="state-message error">{detailError}</p>}
        {selectedIncident && (
          <div className="detail-card">
            <button className="text-button" onClick={() => setSelectedIncident(null)}>Close details</button>
            <h3>{selectedIncident.title}</h3>
            <p>{selectedIncident.description}</p>
            <p className="muted">Status: {selectedIncident.status} · Severity: {selectedIncident.severity}</p>
          </div>
        )}
      </section>

      <section className="report-section" aria-labelledby="report-title">
        <p className="eyebrow">HELP THE COMMUNITY</p>
        <h2 id="report-title">Report a service issue</h2>
        {submitted ? (
          <div className="success-message" role="status">
            Report received: <strong>{submitted.title}</strong>. It is private until reviewed by an operator.
            <button className="text-button" onClick={() => setSubmitted(null)}>Submit another</button>
          </div>
        ) : (
          <>
            {!reviewing ? (
              <form onSubmit={(event) => { event.preventDefault(); setSubmitError(""); setReviewing(true); }}>
                <label>Title<input required maxLength={255} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
                <label>Description<textarea required maxLength={4_000} rows={4} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
                <p className="muted">Reports are private and visible to operators until reviewed.</p>
                <button className="primary-button" type="submit">Review report</button>
              </form>
            ) : (
              <div className="confirmation-card">
                <h3>Confirm your report</h3>
                <p><strong>{title}</strong></p>
                <p>{description}</p>
                <p className="muted">This report will be sent privately to CampusPulse operators for review.</p>
                <div className="button-row">
                  <button className="text-button" onClick={() => setReviewing(false)} disabled={submitting}>Edit</button>
                  <button className="primary-button" onClick={() => void confirmSubmission()} disabled={submitting}>
                    {submitting ? "Submitting…" : "Confirm and submit"}
                  </button>
                </div>
              </div>
            )}
            {submitError && <p role="alert" className="state-message error">{submitError}</p>}
          </>
        )}
      </section>
      <footer>CampusPulse · Synthetic campus service information</footer>
    </main>
  );
}

function parseResult<T extends z.ZodType>(
  result: Awaited<ReturnType<McpApp["callServerTool"]>>,
  schema: T,
): z.infer<T> {
  if (result.isError) {
    const text = result.content.find((item) => item.type === "text");
    throw new Error(text?.type === "text" ? text.text : "The MCP tool request failed.");
  }
  const structured = result.structuredContent;
  let candidate: unknown = structured;
  if (candidate === undefined) {
    const text = result.content.find((item) => item.type === "text");
    if (text?.type !== "text") throw new Error("The MCP tool returned no data.");
    candidate = JSON.parse(text.text) as unknown;
  }
  const parsed = schema.safeParse(candidate);
  if (!parsed.success) throw new Error("The MCP tool returned data in an unexpected format.");
  return parsed.data;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "The request failed unexpectedly.";
}

createRoot(document.getElementById("root")!).render(<AppView />);
