import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { sessionViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";

/**
 * `/checkout`: starts a checkout and moves to its own address. With `?buyNow=<productId>` it is a
 * buy-now of one unit that leaves the cart alone (BRD FR-3.2); without, it checks out the cart. The
 * session lives on the server, so the address it moves to is enough to come back to it.
 */
export function CheckoutStartRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const buyNow = params.get("buyNow");
  const started = useRef(false);

  const start = useMutation({
    mutationFn: () =>
      client.post("/checkout/sessions", {
        body: buyNow ? { buyNowProductId: buyNow } : {},
        schema: sessionViewSchema,
      }),
    onSuccess: (session) => {
      queryClient.setQueryData(["checkout", session.checkoutId, "session"], session);
      void navigate(`/checkout/${encodeURIComponent(session.checkoutId)}`, { replace: true });
    },
  });
  const { mutate } = start;

  useEffect(() => {
    if (!started.current) {
      started.current = true;
      mutate();
    }
  }, [mutate]);

  return (
    <>
      <h1>{t("page.checkout.title")}</h1>
      {start.isError ? (
        <ErrorState
          error={start.error}
          action={<Link to="/cart">{t("checkout.backToCart")}</Link>}
        />
      ) : (
        <Spinner label={t("checkout.starting")} />
      )}
    </>
  );
}
