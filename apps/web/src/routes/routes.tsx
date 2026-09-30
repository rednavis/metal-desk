import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import {
  CartPage,
  CatalogPage,
  CheckoutPage,
  HomePage,
  NotFoundPage,
  SignInPage,
} from "./pages/pages";

/** The storefront's routes. Every URL the app answers is listed here and only here. */
export const appRoutes: RouteObject[] = [
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "catalog", element: <CatalogPage /> },
      { path: "cart", element: <CartPage /> },
      { path: "checkout", element: <CheckoutPage /> },
      { path: "sign-in", element: <SignInPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

/** Where a 401 sends the customer. */
export const SIGN_IN_PATH = "/sign-in";
