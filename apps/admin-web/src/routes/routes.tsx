import type { RouteObject } from "react-router";
import { RequireAuth } from "../features/auth/RequireAuth";
import { AppShell } from "./AppShell";
import { LoginRoute } from "./auth/LoginRoute";
import { OrderDetailRoute } from "./orders/OrderDetailRoute";
import { OrderListRoute } from "./orders/OrderListRoute";
import { NotFoundPage, OverviewPage } from "./pages/pages";
import { QuoteDetailRoute } from "./quotes/QuoteDetailRoute";
import { QuoteQueueRoute } from "./quotes/QuoteQueueRoute";
import { TierEditRoute } from "./tiers/TierEditRoute";
import { TierListRoute } from "./tiers/TierListRoute";

/**
 * The back office's routes. Every URL the app answers is listed here and only here. Everything but
 * the login form is behind {@link RequireAuth}.
 */
export const appRoutes: RouteObject[] = [
  { path: "/login", element: <LoginRoute /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/",
        element: <AppShell />,
        children: [
          { index: true, element: <OverviewPage /> },
          { path: "tiers", element: <TierListRoute /> },
          { path: "tiers/new", element: <TierEditRoute /> },
          { path: "tiers/:tierId", element: <TierEditRoute /> },
          { path: "quotes", element: <QuoteQueueRoute /> },
          { path: "quotes/:orderId", element: <QuoteDetailRoute /> },
          { path: "orders", element: <OrderListRoute /> },
          { path: "orders/:orderId", element: <OrderDetailRoute /> },
          { path: "*", element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
