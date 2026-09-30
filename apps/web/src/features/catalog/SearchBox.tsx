import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, Field } from "../../ui";
import { SEARCH_MIN_LENGTH } from "./limits";
import "./catalog.css";

/** The header's search (BRD FR-1.5): submitting opens the results; a too-short query is refused here, before any request. */
export function SearchBox() {
  const { t } = usePreferences();
  const navigate = useNavigate();
  const [text, setText] = useState("");
  const [tooShort, setTooShort] = useState(false);

  function submit(event: FormEvent) {
    event.preventDefault();
    const query = text.trim();
    if (query.length < SEARCH_MIN_LENGTH) {
      setTooShort(true);
      return;
    }
    setTooShort(false);
    void navigate({ pathname: "/search", search: `?q=${encodeURIComponent(query)}` });
  }

  return (
    <form className="md-search" role="search" onSubmit={submit} noValidate>
      <Field
        label={t("search.label")}
        type="search"
        value={text}
        error={tooShort ? t("search.tooShort", { min: SEARCH_MIN_LENGTH }) : undefined}
        onChange={(event) => {
          setText(event.target.value);
          setTooShort(false);
        }}
      />
      <Button type="submit" variant="secondary">
        {t("search.submit")}
      </Button>
    </form>
  );
}
