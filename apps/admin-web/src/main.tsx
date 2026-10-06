import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { createApp } from "./app/createApp";
import { applyTheme, loadTheme } from "./ui/theme";

// Before the first render, so the page never flashes the wrong theme.
applyTheme(loadTheme());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App app={createApp()} />
  </StrictMode>,
);
