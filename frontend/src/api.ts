import keycloak from './keycloak';

// Calls go to /api on the same origin: nginx (docker) or the Vite dev proxy forwards them
// to the api-gateway, which verifies the token and routes to the queue-service.
const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export type Queue = { id: number; name: string; prefix: string; counter: string; waiting: number; estimatedWaitMinutes: number };
export type Establishment = {
  id: string; name: string; category: string; city: string; shortName: string; color: string; tint: string;
  address: string; opensAt: string; closesAt: string; waiting: number; estimatedWaitMinutes: number; queues: Queue[];
};
export type TicketStatus = 'WAITING' | 'CALLED' | 'SERVED' | 'NO_SHOW' | 'CANCELLED';
export type Ticket = {
  id: string; code: string; status: TicketStatus; establishmentId: string; establishmentName: string;
  establishmentShortName: string; color: string; tint: string; queueId: number; queueName: string; counter: string;
  ownerName: string; position: number; estimatedWaitMinutes: number; createdAt: string; calledAt: string | null;
};
export type Counter = { queue: Queue; current: Ticket | null; waiting: Ticket[] };

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  // refresh the access token if it expires within 30 s
  if (keycloak.authenticated) await keycloak.updateToken(30).catch(() => keycloak.login());
  const res = await fetch(`${BASE}/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {}),
      ...init.headers,
    },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body?.message || body?.error || res.statusText);
  }
  return res.json() as Promise<T>;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  establishments: () => request<Establishment[]>('/establishments'),
  establishment: (id: string) => request<Establishment>(`/establishments/${encodeURIComponent(id)}`),
  takeTicket: (queueId: number) => post<Ticket>('/tickets', { queueId }),
  myTickets: () => request<Ticket[]>('/tickets/me'),
  ticket: (id: string) => request<Ticket>(`/tickets/${encodeURIComponent(id)}`),
  cancelTicket: (id: string) => post<Ticket>(`/tickets/${encodeURIComponent(id)}/cancel`),
  counter: (queueId: number) => request<Counter>(`/queues/${queueId}/counter`),
  callNext: (queueId: number) => post<Counter>(`/queues/${queueId}/call-next`),
  served: (ticketId: string) => post<Counter>(`/tickets/${encodeURIComponent(ticketId)}/served`),
  noShow: (ticketId: string) => post<Counter>(`/tickets/${encodeURIComponent(ticketId)}/no-show`),
};

/** "position 1" -> "1er", 2 -> "2e" */
export const ordinal = (n: number) => (n === 1 ? '1er' : `${n}e`);
