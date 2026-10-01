import type { RouteObject } from "react-router";
import { AppShell } from "./AppShell";
import { HomeRoute } from "./home/HomeRoute";
import { CartRoute } from "./cart/CartRoute";
import { CatalogRoute } from "./catalog/CatalogRoute";
import { CategoryRoute } from "./catalog/CategoryRoute";
import { ProductRoute } from "./catalog/ProductRoute";
import { SearchRoute } from "./catalog/SearchRoute";
import { CheckoutRoute } from "./checkout/CheckoutRoute";
import { CheckoutStartRoute } from "./checkout/CheckoutStartRoute";
import { PaymentCancelRoute, PaymentReturnRoute } from "./checkout/PaymentReturnRoute";
import { RequireAuth } from "../features/auth/RequireAuth";
import { AuthFrame } from "./auth/AuthFrame";
import { ForgotPasswordRoute } from "./auth/ForgotPasswordRoute";
import { RegisterRoute } from "./auth/RegisterRoute";
import { ResetPasswordRoute } from "./auth/ResetPasswordRoute";
import { SignInRoute } from "./auth/SignInRoute";
import { SignOutPrompt } from "./auth/SignOutPrompt";
import { VerifyEmailRoute } from "./auth/VerifyEmailRoute";
import { InquiryRoute } from "./inquiry/InquiryRoute";
import { OrderDetailRoute } from "./orders/OrderDetailRoute";
import { OrderHistoryRoute } from "./orders/OrderHistoryRoute";
import { NotFoundPage } from "./pages/pages";

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
      { path: "checkout", element: <CheckoutStartRoute /> },
      { path: "checkout/:checkoutId", element: <CheckoutRoute /> },
      { path: "checkout/:checkoutId/return", element: <PaymentReturnRoute /> },
      { path: "checkout/:checkoutId/cancel", element: <PaymentCancelRoute /> },
      {
        element: <AuthFrame />,
        children: [
          { path: "sign-in", element: <SignInRoute /> },
          { path: "register", element: <RegisterRoute /> },
          { path: "verify-email", element: <VerifyEmailRoute /> },
          { path: "forgot-password", element: <ForgotPasswordRoute /> },
          { path: "reset-password", element: <ResetPasswordRoute /> },
          { path: "signed-out", element: <SignOutPrompt /> },
        ],
      },
      { path: "inquiry", element: <InquiryRoute /> },
      {
        element: <RequireAuth />,
        children: [
          { path: "orders", element: <OrderHistoryRoute /> },
          { path: "orders/:number", element: <OrderDetailRoute /> },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

/** Where a 401 sends the customer. */
export const SIGN_IN_PATH = "/sign-in";
