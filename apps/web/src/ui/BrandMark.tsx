import { useId } from "react";

/** The MetalDesk logo mark: a faceted ingot in the brand's metal gradient. Decorative only. */
export function BrandMark() {
  const gradient = useId();
  return (
    <svg className="md-brand__mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#4f46e5" />
          <stop offset="0.55" stopColor="#8b5cf6" />
          <stop offset="1" stopColor="#f59e0b" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill={`url(#${gradient})`} />
      <path d="M8 21.5 11 12h10l3 9.5Z" fill="#fff" fillOpacity="0.92" />
      <path d="M11 12h10l-1.6 4.2h-6.8Z" fill="#fff" fillOpacity="0.55" />
    </svg>
  );
}
