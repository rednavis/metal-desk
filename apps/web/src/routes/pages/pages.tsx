import { Link } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";
import { EmptyState } from "../../ui";

/** Route shells: each owns its URL and says what will live there. The screens are M3's (T-052 to T-055). */

export function HomePage() {
  const { t } = usePreferences();
  return (
    <>
      <h1>{t("page.home.title")}</h1>
      <p>{t("page.home.intro")}</p>
    </>
  );
}

export function CatalogPage() {
  const { t } = usePreferences();
  return <h1>{t("page.catalog.title")}</h1>;
}

export function CartPage() {
  const { t } = usePreferences();
  return (
    <>
      <h1>{t("page.cart.title")}</h1>
      <EmptyState
        title={t("page.cart.empty.title")}
        description={t("page.cart.empty.description")}
        action={<Link to="/catalog">{t("page.cart.empty.browse")}</Link>}
      />
    </>
  );
}

export function CheckoutPage() {
  const { t } = usePreferences();
  return <h1>{t("page.checkout.title")}</h1>;
}

export function SignInPage() {
  const { t } = usePreferences();
  return <h1>{t("page.signIn.title")}</h1>;
}

export function NotFoundPage() {
  const { t } = usePreferences();
  return (
    <EmptyState
      title={t("page.notFound.title")}
      description={t("page.notFound.description")}
      action={<Link to="/">{t("page.notFound.home")}</Link>}
    />
  );
}
