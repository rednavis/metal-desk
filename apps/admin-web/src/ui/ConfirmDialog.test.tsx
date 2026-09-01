import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

function open(props: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <ConfirmDialog
      title="Delete tier"
      confirmLabel="Delete"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    >
      Orders keep their price.
    </ConfirmDialog>,
  );
  return { onConfirm, onCancel };
}

describe("ConfirmDialog", () => {
  it("names the consequence and puts focus on the safe choice", () => {
    open();
    expect(screen.getByRole("dialog", { name: "Delete tier" })).toBeInTheDocument();
    expect(screen.getByText("Orders keep their price.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();
  });

  it("confirms and cancels through its buttons, with a custom cancel label", async () => {
    const { onConfirm, onCancel } = open({ cancelLabel: "Keep it" });
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    await userEvent.click(screen.getByRole("button", { name: "Keep it" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("dismisses on Escape", async () => {
    const { onCancel } = open();
    await userEvent.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("keeps Tab inside, wrapping in both directions", async () => {
    open();
    const cancel = screen.getByRole("button", { name: "Cancel" });
    const confirm = screen.getByRole("button", { name: "Delete" });
    await userEvent.tab();
    expect(confirm).toHaveFocus();
    await userEvent.tab();
    expect(cancel).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(confirm).toHaveFocus();
  });

  it("ignores other keys", async () => {
    const { onCancel } = open();
    await userEvent.keyboard("a");
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("holds the confirm button until the acknowledgement is ticked", async () => {
    const { onConfirm } = open({ acknowledgement: "I understand" });
    const confirm = screen.getByRole("button", { name: "Delete" });
    expect(confirm).toBeDisabled();
    await userEvent.click(screen.getByRole("checkbox", { name: "I understand" }));
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("disables confirm while busy", () => {
    open({ busy: true });
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });

  it("returns focus to what had it before", () => {
    const trigger = document.createElement("button");
    document.body.append(trigger);
    trigger.focus();
    const { unmount } = render(
      <ConfirmDialog title="T" confirmLabel="Go" onConfirm={vi.fn()} onCancel={vi.fn()}>
        x
      </ConfirmDialog>,
    );
    unmount();
    expect(trigger).toHaveFocus();
    trigger.remove();
  });
});
