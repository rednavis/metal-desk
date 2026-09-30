import type { Direction, ReferencePriceView } from "../../api/types";
import type { MessageKey } from "../../i18n/messages/en";
import { usePreferences } from "../../preferences/usePreferences";

/** Direction is never colour alone: each state has its own glyph (hidden from screen readers) and a text label. */
const GLYPH: Record<Direction, string> = { UP: "▲", DOWN: "▼", UNCHANGED: "▬" };

const DIRECTION_CLASS: Record<Direction, string> = {
  UP: "md-change--up",
  DOWN: "md-change--down",
  UNCHANGED: "md-change--unchanged",
};

/**
 * One metal's row: its price per gram and the direction and size of its latest change (BRD FR-1.1).
 *
 * The change has **four** states. Up and down carry a glyph, a colour, the size of the move for
 * sighted users, and a sentence naming direction and size for screen readers. Unchanged says so.
 * **Unknown** (the server has seen only one price, so sends no change at all) shows no glyph, no
 * colour and no size, only that the change is not available yet: rendering it as "0.00, unchanged"
 * would tell the customer the price is flat when it is merely unknown.
 */
export function PriceTicker({ price }: { price: ReferencePriceView }) {
  const { t, format } = usePreferences();
  const amount = (value: string) => format.money({ amount: value, currency: price.currency });
  const change = price.change;

  let label: string;
  let visible: string | null = null;
  let className = "md-change--unknown";
  let glyph: string | null = null;
  if (change === undefined) {
    label = t("marketdata.change.unknown");
  } else if (change.direction === "UNCHANGED") {
    label = t("marketdata.change.unchanged");
    className = DIRECTION_CLASS.UNCHANGED;
    glyph = GLYPH.UNCHANGED;
  } else {
    const key: MessageKey =
      change.direction === "UP" ? "marketdata.change.up" : "marketdata.change.down";
    label = t(key, { amount: amount(change.amount), percent: format.percent(change.percent) });
    visible = `${amount(change.amount)} (${format.percent(change.percent)})`;
    className = DIRECTION_CLASS[change.direction];
    glyph = GLYPH[change.direction];
  }

  return (
    <tr data-metal={price.metal} data-direction={change?.direction ?? "UNKNOWN"}>
      <th scope="row">{t(`metal.${price.metal}`)}</th>
      <td>
        {format.money({ amount: price.pricePerGram, currency: price.currency })}{" "}
        <span className="md-prices__unit">{t("marketdata.perGram")}</span>
      </td>
      <td className={`md-change ${className}`}>
        {glyph === null ? (
          label
        ) : (
          <>
            <span aria-hidden="true">
              {glyph} {visible}
            </span>
            <span className="md-sr-only">{label}</span>
          </>
        )}
      </td>
    </tr>
  );
}
