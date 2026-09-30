/**
 * What the customer's preferences do to an outgoing request, in one place.
 *
 * - **Every** request says which language the customer reads in (`Accept-Language`).
 * - Requests for **prices** that may be shown in another currency (the catalog and the cart) carry
 *   `currency`, so the server converts (BRD FR-1.8: the client never multiplies a price).
 * - Requests that **trigger transactional mail** carry `locale` in their body, because the
 *   server renders the mail in it (BRD FR-6.2, FR-8.1). Forgetting this leaves the customer's
 *   invoice in the default language, so the list of such requests is here, tested, and not
 *   scattered over the screens that make them.
 */
export interface RequestContext {
  get(): { locale: string; currency: string };
  set(next: { locale: string; currency: string }): void;
}

export function createRequestContext(initial: {
  locale: string;
  currency: string;
}): RequestContext {
  let current = initial;
  return {
    get: () => current,
    set: (next) => {
      current = next;
    },
  };
}

/** Paths (relative to the API base) whose prices the server can convert: catalog, cart, reference prices. */
const PRICED = [/^\/catalog(\/|$)/, /^\/cart(\/|$)/, /^\/market-data(\/|$)/];

/** Paths whose request triggers a mail that is rendered in the customer's language. */
const MAILING = [
  /^\/account\/register$/,
  /^\/account\/password-reset\/request$/,
  /^\/checkout\/sessions\/[^/]+\/step1$/,
  /^\/checkout\/sessions\/[^/]+\/handoff$/,
  /^\/checkout\/sessions\/[^/]+\/payment\/execute$/,
  /^\/inquiries$/,
];

export function carriesCurrency(path: string): boolean {
  return PRICED.some((pattern) => pattern.test(path));
}

export function triggersMail(method: string, path: string): boolean {
  return method !== "GET" && MAILING.some((pattern) => pattern.test(path));
}

/**
 * The body to send: the given one with the customer's `locale` added if the request triggers mail
 * and the caller did not choose a locale itself. A mailing request with no body gets `{locale}`.
 */
export function withLocale(method: string, path: string, body: unknown, locale: string): unknown {
  if (!triggersMail(method, path)) return body;
  if (body === undefined) return { locale };
  if (typeof body === "object" && body !== null && !Array.isArray(body)) {
    return "locale" in body ? body : { ...body, locale };
  }
  return body;
}
