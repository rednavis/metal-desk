import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import { NotFoundPage, OrdersPage, OverviewPage, QuotesPage, TiersPage } from "./pages/pages";

/** The back office's routes. Every URL the app answers is listed here and only here. */
export const appRoutes: RouteObject[] = [
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <OverviewPage /> },
      { path: "tiers", element: <TiersPage /> },
      { path: "quotes", element: <QuotesPage /> },
      { path: "orders", element: <OrdersPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];
