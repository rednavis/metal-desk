import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import { HomeRoute } from "./home/HomeRoute";
import { CartRoute } from "./cart/CartRoute";
import { CatalogRoute } from "./catalog/CatalogRoute";
import { CategoryRoute } from "./catalog/CategoryRoute";
import { ProductRoute } from "./catalog/ProductRoute";
import { SearchRoute } from "./catalog/SearchRoute";
import { CheckoutPage, NotFoundPage, SignInPage } from "./pages/pages";

/** The storefront's routes. Every URL the app answers is listed here and only here. */
export const appRoutes: RouteObject[] = [
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomeRoute /> },
      { path: "catalog", element: <CatalogRoute /> },
      { path: "catalog/categories/:categoryId", element: <CategoryRoute /> },
      { path: "catalog/products/:productId", element: <ProductRoute /> },
      { path: "search", element: <SearchRoute /> },
      { path: "cart", element: <CartRoute /> },
      { path: "checkout", element: <CheckoutPage /> },
      { path: "sign-in", element: <SignInPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

/** Where a 401 sends the customer. */
export const SIGN_IN_PATH = "/sign-in";
