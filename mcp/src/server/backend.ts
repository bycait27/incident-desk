import { z } from "zod";
import type { McpConfig } from "./config.js";

const incidentSchema = z.object({
  id: z.number().int().positive(),
  title: z.string(),
  description: z.string(),
  status: z.string(),
  severity: z.string(),
  published: z.boolean(),
  archived: z.boolean(),
}).passthrough();

const monitorStatusSchema = z.object({
  available: z.boolean(),
  stale: z.boolean(),
  observedAt: z.string().datetime({ offset: true }).nullable(),
  message: z.string().nullable(),
  monitors: z.array(z.object({
    id: z.string(),
    name: z.string(),
    url: z.string().url(),
    status: z.string(),
    lastCheckedAt: z.string().datetime({ offset: true }).nullable(),
  })),
});

export type Incident = z.infer<typeof incidentSchema>;
export type MonitorStatus = z.infer<typeof monitorStatusSchema>;

export class BackendApi {
  constructor(
    private readonly config: McpConfig,
    private readonly timeoutMs = 5_000,
  ) {}

  async listIncidents(token: string): Promise<Incident[]> {
    const payload = await this.request(token, "/incidents");
    return z.array(incidentSchema).parse(payload);
  }

  async getIncident(token: string, id: number): Promise<Incident> {
    const payload = await this.request(token, `/incidents/${id}`);
    return incidentSchema.parse(payload);
  }

  async getMonitorStatus(token: string): Promise<MonitorStatus> {
    const payload = await this.request(token, "/uptime");
    return monitorStatusSchema.parse(payload);
  }

  async submitIncident(
    token: string,
    input: { title: string; description: string },
  ): Promise<Incident> {
    const payload = await this.request(token, "/incidents", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return incidentSchema.parse(payload);
  }

  async auditToolCall(
    token: string,
    input: {
      toolName: string;
      outcome: "STARTED" | "AWAITING_CONFIRMATION" | "SUCCEEDED" | "FAILED" | "DENIED" | "CANCELLED";
      resourceId?: number;
    },
  ): Promise<void> {
    const response = await fetch(`${this.config.backendApiBaseUrl}/tool-audit`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(input),
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    if (!response.ok) {
      throw new Error(`MCP audit API returned HTTP ${response.status}.`);
    }
  }

  private async request(
    token: string,
    path: string,
    init: RequestInit = {},
  ): Promise<unknown> {
    const response = await fetch(`${this.config.backendApiBaseUrl}${path}`, {
      ...init,
      headers: {
        ...init.headers,
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    if (!response.ok) {
      throw new Error(`CampusPulse API returned HTTP ${response.status}.`);
    }
    return response.json();
  }
}
