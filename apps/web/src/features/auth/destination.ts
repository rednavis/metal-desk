/** Where to go after signing in: the page that sent the customer here, if it is one of ours. */
export function safeDestination(from: string | null): string {
  return from !== null && from.startsWith("/") && !from.startsWith("//") ? from : "/";
}
