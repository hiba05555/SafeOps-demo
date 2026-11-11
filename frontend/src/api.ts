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
export type AppointmentStatus = 'BOOKED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';
export type Appointment = {
  id: string;
  ownerSubject: string;
  establishmentId: string;
  queueId: number;
  startsAt: string;
  reason: string;
  status: AppointmentStatus;
  createdAt: string;
};
export type Preferences = {
  subject: string;
  sms: boolean;
  email: boolean;
  appointmentReminder: boolean;
  language: string;
};
export type NotificationType = 'TURN_APPROACHING' | 'APPOINTMENT_REMINDER';
export type Notification = {
  id: string;
  subject: string;
  type: NotificationType;
  message: string;
  read: boolean;
  createdAt: string;
};

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
    const body = await res.json().catch(() => null) as { message?: string; error?: string } | null;
    throw new ApiError(res.status, body?.message || body?.error || res.statusText);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
const put = <T>(path: string, body: unknown) =>
  request<T>(path, { method: 'PUT', body: JSON.stringify(body) });

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
  appointments: () => request<Appointment[]>('/appointments/me'),
  createAppointment: (input: Pick<Appointment, 'establishmentId' | 'queueId' | 'startsAt' | 'reason'>) =>
    post<Appointment>('/appointments', input),
  cancelAppointment: (id: string) => post<Appointment>(`/appointments/${encodeURIComponent(id)}/cancel`),
  preferences: () => request<Preferences>('/profile/preferences'),
  updatePreferences: (input: Omit<Preferences, 'subject'>) => put<Preferences>('/profile/preferences', input),
  notifications: () => request<Notification[]>('/notifications'),
  markNotificationRead: (id: string) => post<Notification>(`/notifications/${encodeURIComponent(id)}/read`),
};

/** "position 1" -> "1er", 2 -> "2e" */
export const ordinal = (n: number) => (n === 1 ? '1er' : `${n}e`);
