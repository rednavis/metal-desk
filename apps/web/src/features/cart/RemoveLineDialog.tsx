import { useEffect, useId, useRef, type KeyboardEvent } from "react";
import { usePreferences } from "../../preferences/usePreferences";
import { Button } from "../../ui";

interface RemoveLineDialogProps {
  name: string;
  onConfirm: () => void;
  onCancel: () => void;
}

const FOCUSABLE = "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])";

/**
 * The confirmation before a line is removed (BRD FR-3.3). A modal dialog: focus moves in when it
 * opens, on the safe choice ("keep", never the destructive one), Tab and Shift+Tab stay inside it,
 * Escape dismisses it, and focus returns to what had it before. The page behind is unreachable by
 * keyboard while it is open, which is what makes it usable without a mouse.
 */
export function RemoveLineDialog({ name, onConfirm, onCancel }: RemoveLineDialogProps) {
  const { t } = usePreferences();
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  const keep = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const opener = document.activeElement;
    keep.current?.focus();
    return () => {
      if (opener instanceof HTMLElement) opener.focus();
    };
  }, []);

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === "Escape") {
      event.stopPropagation();
      onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = [...(dialog.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])];
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (first === undefined || last === undefined) return;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="md-dialog-backdrop">
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="md-dialog"
        onKeyDown={onKeyDown}
      >
        <h2 id={titleId}>{t("cart.remove.title")}</h2>
        <p>{t("cart.remove.body", { name })}</p>
        <div className="md-actions">
          <Button ref={keep} variant="secondary" onClick={onCancel}>
            {t("cart.remove.cancel")}
          </Button>
          <Button onClick={onConfirm}>{t("cart.remove.confirm")}</Button>
        </div>
      </div>
    </div>
  );
}
