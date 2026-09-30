import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import { OrderDetailRoute } from "./orders/OrderDetailRoute";
import { OrderListRoute } from "./orders/OrderListRoute";
import { NotFoundPage, OverviewPage } from "./pages/pages";
import { QuoteDetailRoute } from "./quotes/QuoteDetailRoute";
import { QuoteQueueRoute } from "./quotes/QuoteQueueRoute";
import { TierEditRoute } from "./tiers/TierEditRoute";
import { TierListRoute } from "./tiers/TierListRoute";

/** The back office's routes. Every URL the app answers is listed here and only here. */
export const appRoutes: RouteObject[] = [
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
];
