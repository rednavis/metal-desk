import { orderStatusSchema } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";

/**
 * An order's status as words (BRD FR-10.1). A status this app knows is shown in the customer's
 * language; one it does not know yet (added on the server later) is shown by the label the server
 * sent, and if that too is empty by the status name, so a status can never render blank.
 */
export function OrderStatusBadge({ status, statusLabel }: { status: string; statusLabel: string }) {
  const { t } = usePreferences();
  const known = orderStatusSchema.safeParse(status);
  const text = known.success ? t(`order.status.${known.data}`) : statusLabel || status;
  return (
    <span className="md-status" data-status={status}>
      {text}
    </span>
  );
}
