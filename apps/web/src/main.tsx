import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { createApp } from "./app/createApp";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App app={createApp()} />
  </StrictMode>,
);
