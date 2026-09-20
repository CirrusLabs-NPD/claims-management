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
 * Downloads a binary export (CSV/Excel/PDF) with the bearer token attached and
 * saves it to disk. A plain <a href> cannot carry the Authorization header, so
 * we fetch the bytes, honour the server's Content-Disposition filename, and
 * surface failures as the same typed ApiError the JSON path uses.
 */
export async function downloadExport(
  path: string,
  query: Options['query'],
  fallbackName: string,
): Promise<void> {
  const token = tokenStore.get();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const response = await fetch(`/api${path}${buildQuery(query)}`, { headers });

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
      `Export failed with status ${response.status}`;
    throw new ApiError(response.status, detail);
  }

  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get('Content-Disposition')) ?? fallbackName;

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8) { try { return decodeURIComponent(utf8[1]); } catch { /* fall through */ } }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}
