import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import { HomeRoute } from "./home/HomeRoute";
import { CartPage, CatalogPage, CheckoutPage, NotFoundPage, SignInPage } from "./pages/pages";

/** The storefront's routes. Every URL the app answers is listed here and only here. */
export const appRoutes: RouteObject[] = [
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomeRoute /> },
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
