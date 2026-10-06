import { useCurrencyOptions } from "../preferences/useCurrencyOptions";
import { usePreferences } from "../preferences/usePreferences";
import { LOCALES, isLocale } from "../i18n/locales";
import { THEMES, isTheme } from "../theme/theme";

/**
 * The three controls of BRD FR-1.6 to FR-1.8: theme, language and currency. When the rates are
 * demo rates, it says so next to the currency, and that checkout is always in the settlement
 * currency, so a customer browsing in one currency is never surprised by another at payment.
 */
export function PreferenceSwitcher() {
  const { theme, locale, currency, setTheme, setLocale, setCurrency, t } = usePreferences();
  const options = useCurrencyOptions();
  const codes = options.data?.options.map((option) => option.code) ?? [currency];
  const settlement = options.data?.settlement;
  const fake = options.data?.rateSource === "FAKE";

  return (
    <div className="md-switcher">
      <label>
        {t("switcher.theme")}
        <select
          value={theme}
          onChange={(event) => {
            if (isTheme(event.target.value)) setTheme(event.target.value);
          }}
        >
          {THEMES.map((choice) => (
            <option key={choice} value={choice}>
              {t(`theme.${choice}`)}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t("switcher.language")}
        <select
          value={locale}
          onChange={(event) => {
            if (isLocale(event.target.value)) setLocale(event.target.value);
          }}
        >
          {Object.entries(LOCALES).map(([code, language]) => (
            <option key={code} value={code}>
              {language.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t("switcher.currency")}
        <select
          value={currency}
          onChange={(event) => {
            setCurrency(event.target.value);
          }}
        >
          {codes.map((code) => (
            <option key={code} value={code}>
              {code}
            </option>
          ))}
        </select>
      </label>
      {settlement && currency !== settlement ? (
        <p role="note">
          {fake
            ? t("currency.fakeRates", { currency, settlement })
            : t("currency.settlementOnly", { settlement })}
        </p>
      ) : null}
    </div>
  );
}
