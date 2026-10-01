import { Link } from "react-router";
import { MarketDataPanel } from "../../features/marketdata/MarketDataPanel";
import type { MessageKey } from "../../i18n/messages/en";
import { usePreferences } from "../../preferences/usePreferences";
import "./home.css";

const FEATURES: readonly { title: MessageKey; body: MessageKey }[] = [
  { title: "home.feature.live.title", body: "home.feature.live.body" },
  { title: "home.feature.transparent.title", body: "home.feature.transparent.body" },
  { title: "home.feature.quote.title", body: "home.feature.quote.body" },
];

/** The landing screen: what MetalDesk is, and the live reference prices (BRD FR-1.1). */
export function HomeRoute() {
  const { t } = usePreferences();
  return (
    <>
      <section className="md-hero" aria-labelledby="home-title">
        <p className="md-hero__eyebrow">{t("home.hero.eyebrow")}</p>
        <h1 id="home-title">{t("page.home.title")}</h1>
        <p className="md-hero__lead">{t("page.home.intro")}</p>
        <p className="md-hero__description">{t("home.hero.description")}</p>
        <div className="md-actions">
          <Link to="/catalog" className="md-button md-button--lg md-hero__cta">
            {t("home.hero.browse")}
          </Link>
          <Link to="/inquiry" className="md-button md-button--lg md-hero__cta--ghost">
            {t("home.hero.contact")}
          </Link>
        </div>
      </section>
      <div className="md-home-grid">
        <div className="md-card">
          <MarketDataPanel />
        </div>
        <section className="md-features" aria-labelledby="home-features">
          <h2 id="home-features" className="md-sr-only">
            {t("home.features.title")}
          </h2>
          {FEATURES.map((feature) => (
            <article key={feature.title} className="md-feature">
              <h3>{t(feature.title)}</h3>
              <p>{t(feature.body)}</p>
            </article>
          ))}
        </section>
      </div>
    </>
  );
}
