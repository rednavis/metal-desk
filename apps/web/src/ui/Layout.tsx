import type { ReactNode } from "react";
import { Link } from "react-router";
import { BrandMark } from "./BrandMark";

interface LayoutProps {
  /** The application's name, shown in the header as the link home. */
  title: string;
  /** Navigation links. */
  nav?: ReactNode;
  /** The header's right-hand side: search and the like. */
  actions?: ReactNode;
  /** A slim bar above the header for the language, currency and theme switches (BRD FR-1.6 to FR-1.8). */
  utility?: ReactNode;
  /** The page footer. */
  footer?: ReactNode;
  /** The text of the keyboard "skip to content" link, in the active language. */
  skipLabel?: string;
  children: ReactNode;
}

/**
 * The page frame: an optional utility bar, a sticky header (brand, navigation, actions), the page,
 * and an optional footer. The first focusable element is a skip link to the page's content.
 */
export function Layout({ title, nav, actions, utility, footer, skipLabel, children }: LayoutProps) {
  return (
    <div className="md-layout">
      {skipLabel ? (
        <a className="md-skip-link" href="#main-content">
          {skipLabel}
        </a>
      ) : null}
      {utility ? (
        <div className="md-utility">
          <div className="md-utility__inner">{utility}</div>
        </div>
      ) : null}
      <header className="md-layout__header">
        <div className="md-layout__header-inner">
          <Link to="/" className="md-brand">
            <BrandMark />
            <span>{title}</span>
          </Link>
          {nav ? <nav className="md-layout__nav">{nav}</nav> : null}
          {actions ? <div className="md-layout__actions">{actions}</div> : null}
        </div>
      </header>
      <main id="main-content" className="md-main" tabIndex={-1}>
        {children}
      </main>
      {footer ? (
        <footer className="md-footer">
          <div className="md-footer__inner">{footer}</div>
        </footer>
      ) : null}
    </div>
  );
}
