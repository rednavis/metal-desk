import type { ReactNode } from "react";
import { Link } from "react-router";
import { BrandMark } from "./BrandMark";

interface LayoutProps {
  /** The application's name, shown at the top of the sidebar as the link to the overview. */
  title: string;
  /** Navigation links, laid out down the sidebar. */
  nav?: ReactNode;
  /** The top bar's contents: who is signed in, and sign-out. */
  actions?: ReactNode;
  children: ReactNode;
}

/** The back-office frame: a sidebar with the brand and navigation, a top bar, and the page. */
export function Layout({ title, nav, actions, children }: LayoutProps) {
  return (
    <div className="md-layout">
      <aside className="md-sidebar">
        <Link to="/" className="md-brand">
          <BrandMark />
          <span>{title}</span>
        </Link>
        {nav ? (
          <nav className="md-layout__nav" aria-label="Sections">
            {nav}
          </nav>
        ) : null}
      </aside>
      <div className="md-layout__body">
        {actions ? <header className="md-layout__header">{actions}</header> : null}
        <main className="md-main">{children}</main>
      </div>
    </div>
  );
}
