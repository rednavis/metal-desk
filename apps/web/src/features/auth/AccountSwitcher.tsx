import { useState } from "react";
import { usePreferences } from "../../preferences/usePreferences";
import { Button } from "../../ui";
import { useAuth } from "./useAuth";

/** Switching to another account signed in to in this tab, with no password prompt (BRD FR-2.5); absent when there is none. */
export function AccountSwitcher() {
  const { t } = usePreferences();
  const { otherAccounts, switchTo } = useAuth();
  const [target, setTarget] = useState<string | undefined>();
  const [failed, setFailed] = useState(false);
  if (otherAccounts.length === 0) return null;
  const chosen = target ?? otherAccounts[0]?.customerId;

  return (
    <form
      className="md-switcher"
      onSubmit={(event) => {
        event.preventDefault();
        if (chosen === undefined) return;
        setFailed(false);
        switchTo(chosen).catch(() => {
          setFailed(true);
        });
      }}
    >
      <label>
        <span>{t("account.switch.label")}</span>
        <select
          value={chosen}
          onChange={(event) => {
            setTarget(event.target.value);
          }}
        >
          {otherAccounts.map((account) => (
            <option key={account.customerId} value={account.customerId}>
              {account.label}
            </option>
          ))}
        </select>
      </label>
      <Button type="submit" variant="secondary">
        {t("account.switch.submit")}
      </Button>
      {failed ? <span role="alert">{t("auth.signIn.failed")}</span> : null}
    </form>
  );
}
