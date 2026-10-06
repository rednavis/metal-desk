import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { createApp } from "./app/createApp";
import { PreferencesProvider } from "./preferences/PreferencesProvider";
import "./theme/tokens.css";

const app = createApp();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <PreferencesProvider
      client={app.client}
      tokenStore={app.tokenStore}
      queryClient={app.queryClient}
      requestContext={app.requestContext}
    >
      <App app={app} />
    </PreferencesProvider>
  </StrictMode>,
);
