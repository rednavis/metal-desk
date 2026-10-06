import { useSearchParams } from "react-router";
import type { InquirySource } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";
import { InquiryForm } from "./InquiryForm";

/**
 * `/inquiry`, the one address for all three entry points (BRD FR-9.1): `?productId=` from a product
 * page or a "request price" link, `?handoffReference=` after a manager handoff, neither for a general
 * question from the catalog. The entry point decides the source the server records; it does not need
 * a sign-in.
 */
export function InquiryRoute() {
  const { t } = usePreferences();
  const [params] = useSearchParams();
  const productId = params.get("productId") ?? undefined;
  const handoffReference = params.get("handoffReference") ?? undefined;
  const source: InquirySource = handoffReference ? "HANDOFF" : productId ? "PRODUCT" : "CATALOG";
  return (
    <>
      <h1>{t("inquiry.title")}</h1>
      {source === "PRODUCT" && productId ? (
        <p>{t("inquiry.about.product", { id: productId })}</p>
      ) : null}
      {source === "HANDOFF" && handoffReference ? (
        <p>{t("inquiry.about.handoff", { reference: handoffReference })}</p>
      ) : null}
      <InquiryForm source={source} productId={productId} handoffReference={handoffReference} />
    </>
  );
}
