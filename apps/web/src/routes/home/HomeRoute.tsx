import { MarketDataPanel } from "../../features/marketdata/MarketDataPanel";
import { usePreferences } from "../../preferences/usePreferences";

/** The landing screen: what MetalDesk is, and the live reference prices (BRD FR-1.1). */
export function HomeRoute() {
  const { t } = usePreferences();
  return (
    <>
      <h1>{t("page.home.title")}</h1>
      <p>{t("page.home.intro")}</p>
      <MarketDataPanel />
    </>
  );
}
