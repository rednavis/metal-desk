import type { ComponentProps } from "react";

interface ButtonProps extends ComponentProps<"button"> {
  variant?: "primary" | "secondary";
}

/** A button. It is `type="button"` unless told otherwise, so it never submits a form by accident. */
export function Button({ variant = "primary", type = "button", className, ...rest }: ButtonProps) {
  const classes = ["md-button", variant === "secondary" ? "md-button--secondary" : "", className]
    .filter(Boolean)
    .join(" ");
  return <button type={type} className={classes} {...rest} />;
}
