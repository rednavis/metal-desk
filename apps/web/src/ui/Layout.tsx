import type { ReactNode } from "react";

interface LayoutProps {
  /** The application's name, shown in the header. */
  title: string;
  /** Navigation links. */
  nav?: ReactNode;
  /** Room for the language, currency and theme switches (BRD FR-1.6 to FR-1.8). */
  actions?: ReactNode;
  children: ReactNode;
}

/** The page frame: a header with the title, navigation and actions, and the page below it. */
export function Layout({ title, nav, actions, children }: LayoutProps) {
  return (
    <div className="md-layout">
      <header className="md-layout__header">
        <strong>{title}</strong>
        {nav ? <nav className="md-layout__nav">{nav}</nav> : null}
        {actions ? <div>{actions}</div> : null}
      </header>
      <main>{children}</main>
    </div>
  );
}
