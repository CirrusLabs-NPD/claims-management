const TOKEN_KEY = 'cms.token';
const USER_KEY = 'cms.user';

export class ApiError extends Error {
  status: number;
  detail: string;
  fieldErrors: Record<string, string>;
  phase?: number;

  constructor(status: number, detail: string, fieldErrors: Record<string, string> = {}, phase?: number) {
    super(detail);
    this.status = status;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
    this.phase = phase;
  }
}

export const tokenStore = {
  get: (): string | null => {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set: (token: string) => {
    try { localStorage.setItem(TOKEN_KEY, token); } catch { /* private mode */ }
  },
  clear: () => {
    try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch { /* ignore */ }
  },
  getUser: <T,>(): T | null => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch { return null; }
  },
  setUser: (user: unknown) => {
    try { localStorage.setItem(USER_KEY, JSON.stringify(user)); } catch { /* ignore */ }
  },
};

type Options = {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null | string[]>;
};

function buildQuery(query: Options['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      value.forEach((v) => params.append(key, String(v)));
    } else {
      params.append(key, String(value));
    }
  }
  const s = params.toString();
  return s ? `?${s}` : '';
}

/**
 * Single entry point for every call. Attaches the bearer token, unwraps
 * RFC 9457 problem+json into a typed ApiError, and signs the user out on 401.
 */
export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const token = tokenStore.get();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const response = await fetch(`/api${path}${buildQuery(options.query)}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 401 && !path.startsWith('/auth/login')) {
    tokenStore.clear();
    window.location.hash = '#/login';
    throw new ApiError(401, 'Your session has expired. Sign in again.');
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const payload = text ? safeParse(text) : null;

  if (!response.ok) {
    const detail =
      (payload?.detail as string) ??
      (payload?.message as string) ??
      `Request failed with status ${response.status}`;
    const fieldErrors = (payload?.errors as Record<string, string>) ?? {};
    const phase = payload?.phase as number | undefined;
    throw new ApiError(response.status, detail, fieldErrors, phase);
  }

  return payload as T;
}

function safeParse(text: string): Record<string, unknown> | null {
  try { return JSON.parse(text) as Record<string, unknown>; } catch { return null; }
}

/**
 * Downloads a file (CSV, xlsx, PDF) from an authenticated endpoint. Mirrors the
 * `api` wrapper — bearer token, query builder, RFC 9457 problem+json unwrapped
 * into an ApiError, sign-out on 401 — but keeps the body as a blob and saves it
 * with the filename the server sets in Content-Disposition.
 */
export async function download(path: string, options: Options = {}): Promise<void> {
  const token = tokenStore.get();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const response = await fetch(`/api${path}${buildQuery(options.query)}`, {
    method: options.method ?? 'GET',
    headers,
  });

  if (response.status === 401) {
    tokenStore.clear();
    window.location.hash = '#/login';
    throw new ApiError(401, 'Your session has expired. Sign in again.');
  }

  if (!response.ok) {
    const text = await response.text();
    const payload = text ? safeParse(text) : null;
    const detail =
      (payload?.detail as string) ??
      (payload?.message as string) ??
      `Download failed with status ${response.status}`;
    throw new ApiError(response.status, detail, (payload?.errors as Record<string, string>) ?? {});
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filenameFromDisposition(response.headers.get('Content-Disposition'));
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/** Pulls the filename out of a Content-Disposition header; falls back if absent. */
function filenameFromDisposition(header: string | null): string {
  if (!header) return 'download';
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8) return decodeURIComponent(utf8[1]);
  const quoted = /filename="?([^";]+)"?/i.exec(header);
  return quoted ? quoted[1] : 'download';
}
