import type { z } from "zod";
import { ApiError } from "./errors";
import { carriesCurrency, withLocale, type RequestContext } from "./requestContext";
import type { TokenStore } from "./tokenStore";

/** Options of a single call. */
export interface RequestOptions<T> {
  /** Query parameters; `undefined` values are left out. */
  query?: Record<string, string | number | boolean | undefined>;
  /** A JSON body. */
  body?: unknown;
  /** If given, a success body must match it, or the call fails with `response.malformed`. */
  schema?: z.ZodType<T>;
  signal?: AbortSignal;
  /** Use this correlation id instead of a fresh one, so the caller can show it if the call fails. */
  correlationId?: string;
}

export interface ApiClientOptions {
  /** Where calls go; the dev-server proxy path by default, never a hard-coded host. */
  baseUrl?: string;
  /** The customer's token, sent as a bearer credential if there is one. */
  tokenStore?: TokenStore;
  /** Called after a 401, once the token has been cleared; the app routes to sign-in here. */
  onUnauthorized?: () => void;
  /** The customer's language and display currency, applied to requests; see `requestContext.ts`. */
  requestContext?: RequestContext;
  /** Replaces the platform's `fetch`, for tests. */
  fetchImpl?: typeof globalThis.fetch;
}

export interface ApiClient {
  get<T>(path: string, options?: RequestOptions<T>): Promise<T>;
  post<T>(path: string, options?: RequestOptions<T>): Promise<T>;
  put<T>(path: string, options?: RequestOptions<T>): Promise<T>;
  delete<T>(path: string, options?: RequestOptions<T>): Promise<T>;
}

/**
 * The only place in the app that talks to the network. It applies the base URL, the bearer token
 * and a correlation id to every call, turns every failure into an {@link ApiError}, and on a 401
 * clears the token and calls `onUnauthorized`. Screens call this (through TanStack Query) and never
 * `fetch`, so none of them can forget the envelope handling.
 */
export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = (options.baseUrl ?? import.meta.env.VITE_API_BASE_URL ?? "/api").replace(
    /\/$/,
    "",
  );

  async function send<T>(method: string, path: string, call: RequestOptions<T>): Promise<T> {
    const correlationId = call.correlationId ?? crypto.randomUUID();
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Correlation-Id": correlationId,
    };
    const token = options.tokenStore?.get();
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const preferences = options.requestContext?.get();
    if (preferences) headers["Accept-Language"] = preferences.locale;
    const payload = preferences
      ? withLocale(method, path, call.body, preferences.locale)
      : call.body;
    if (payload !== undefined) headers["Content-Type"] = "application/json";
    const query =
      preferences && carriesCurrency(path)
        ? { currency: preferences.currency, ...call.query }
        : call.query;

    let response: Response;
    try {
      const transport: typeof globalThis.fetch =
        options.fetchImpl ?? ((input, init) => globalThis.fetch(input, init));
      response = await transport(url(baseUrl, path, query), {
        method,
        headers,
        body: payload === undefined ? undefined : JSON.stringify(payload),
        credentials: "same-origin",
        signal: call.signal,
      });
    } catch (failure) {
      if (failure instanceof DOMException && failure.name === "AbortError") throw failure;
      throw ApiError.unreachable(correlationId);
    }

    const body = await readBody(response);
    if (!response.ok) {
      if (response.status === 401) {
        options.tokenStore?.clear();
        options.onUnauthorized?.();
      }
      throw ApiError.fromResponse(response.status, body, correlationId);
    }
    if (call.schema === undefined) return body as T;
    const parsed = call.schema.safeParse(body);
    if (!parsed.success) throw ApiError.malformed(correlationId);
    return parsed.data;
  }

  return {
    get: (path, call = {}) => send("GET", path, call),
    post: (path, call = {}) => send("POST", path, call),
    put: (path, call = {}) => send("PUT", path, call),
    delete: (path, call = {}) => send("DELETE", path, call),
  };
}

function url(base: string, path: string, query: RequestOptions<unknown>["query"]): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) search.set(key, String(value));
  }
  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return `${base}${path.startsWith("/") ? path : `/${path}`}${suffix}`;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (text === "") return undefined;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
}
