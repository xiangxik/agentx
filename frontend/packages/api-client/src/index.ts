export type ApiMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequestOptions<TBody = unknown> {
  path: string;
  method?: ApiMethod;
  body?: TBody;
  token?: string;
}

const resolveBaseUrl = () => {
  if (typeof globalThis !== 'undefined' && 'location' in globalThis) {
    return (globalThis as typeof globalThis & { __AGENTX_API_BASE_URL__?: string }).__AGENTX_API_BASE_URL__ ?? 'http://localhost:8080';
  }

  return 'http://localhost:8080';
};

export async function apiRequest<TResponse, TBody = unknown>(
  options: ApiRequestOptions<TBody>
): Promise<TResponse> {
  const response = await fetch(`${resolveBaseUrl()}${options.path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }

  return (await response.json()) as TResponse;
}

export interface HealthResponse {
  status: string;
  service: string;
}
