import { Link } from "react-router";
import { EmptyState } from "../../ui";

/** Route shells: each owns its URL and says what will live there. The screens are M3's (T-052 to T-055). */

export function HomePage() {
  return (
    <>
      <h1>MetalDesk</h1>
      <p>Live reference prices and the catalog will appear here.</p>
    </>
  );
}

export function CatalogPage() {
  return <h1>Catalog</h1>;
}

export function CartPage() {
  return (
    <>
      <h1>Cart</h1>
      <EmptyState
        title="Your cart is empty"
        description="Add something from the catalog to start an order."
        action={<Link to="/catalog">Browse the catalog</Link>}
      />
    </>
  );
}

export function CheckoutPage() {
  return <h1>Checkout</h1>;
}

export function SignInPage() {
  return <h1>Sign in</h1>;
}

export function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you asked for does not exist."
      action={<Link to="/">Go to the home page</Link>}
    />
  );
}
