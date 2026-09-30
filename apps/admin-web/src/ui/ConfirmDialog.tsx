import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { Button } from "./Button";

interface ConfirmDialogProps {
  title: string;
  /** What will happen, in words that name the consequence. */
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  /**
   * If given, the confirm button stays disabled until this statement is ticked: for a consequence
   * that must be read and accepted, not just clicked through.
   */
  acknowledgement?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const FOCUSABLE = "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])";

/**
 * A confirmation for a destructive or financial action. A modal dialog: focus moves in on the safe
 * choice (cancel), never on the destructive one; Tab stays inside; Escape dismisses it; focus returns
 * to what had it before. The consequence is written in the body, so a click is an informed one.
 */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  cancelLabel = "Cancel",
  acknowledgement,
  busy,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const [acknowledged, setAcknowledged] = useState(false);

  useEffect(() => {
    const opener = document.activeElement;
    cancel.current?.focus();
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
    const focusable = [...(dialog.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])].filter(
      (element) => !element.hasAttribute("disabled"),
    );
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
        <h2 id={titleId}>{title}</h2>
        <div>{children}</div>
        {acknowledgement ? (
          <label className="md-acknowledge">
            <input
              type="checkbox"
              checked={acknowledged}
              onChange={(event) => {
                setAcknowledged(event.target.checked);
              }}
            />
            <span>{acknowledgement}</span>
          </label>
        ) : null}
        <div className="md-actions">
          <Button ref={cancel} variant="secondary" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button
            disabled={busy || (acknowledgement !== undefined && !acknowledged)}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
