import { useMutation } from "@tanstack/react-query";
import { useId, useState, type FormEvent } from "react";
import { isApiError } from "../../api/errors";
import { inquiryReceiptSchema, type InquiryRequest, type InquirySource } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, Field } from "../../ui";

interface InquiryFormProps {
  source: InquirySource;
  /** The product an inquiry from a product page is about. */
  productId?: string;
  /** The order number an inquiry after a manager handoff is about. */
  handoffReference?: string;
}

/**
 * A message to staff (BRD FR-9.1, FR-9.2): a price inquiry about a product, a question about an order
 * handed to a manager, or a general question. It works for anonymous visitors, so it asks for a name
 * and an email and needs no sign-in; the `source` it sends is how the server files it. The server's
 * field-by-field answer is shown beside the inputs, and the reference it returns is shown once the
 * message is stored.
 */
export function InquiryForm({ source, productId, handoffReference }: InquiryFormProps) {
  const { t } = usePreferences();
  const client = useApi();
  const messageId = useId();
  const [form, setForm] = useState({ name: "", email: "", topic: "", message: "" });
  const send = useMutation({
    mutationFn: () => {
      const request: InquiryRequest = {
        source,
        name: form.name.trim(),
        email: form.email.trim(),
        topic: form.topic.trim(),
        message: form.message.trim(),
        ...(source === "PRODUCT" && productId ? { productId } : {}),
        ...(source === "HANDOFF" && handoffReference ? { handoffReference } : {}),
      };
      return client.post("/inquiries", { body: request, schema: inquiryReceiptSchema });
    },
  });

  if (send.isSuccess) {
    return (
      <section aria-labelledby="inquiry-sent-title" data-testid="inquiry-sent">
        <h2 id="inquiry-sent-title">{t("inquiry.sent.title")}</h2>
        <p data-testid="inquiry-reference">
          {t("inquiry.sent.reference", { reference: send.data.reference })}
        </p>
        <p>{send.data.message}</p>
      </section>
    );
  }

  const violations = isApiError(send.error) ? send.error.violations : [];
  const message = (field: string) => violations.find((v) => v.field === field)?.message;
  const context = ["productId", "handoffReference"].flatMap((field) => message(field) ?? []);
  const set = (field: keyof typeof form) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };
  const messageError = message("message");
  return (
    <form
      noValidate
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        send.mutate();
      }}
    >
      {violations.length > 0 ? <p role="alert">{t("inquiry.invalid")}</p> : null}
      {context.map((text) => (
        <p key={text} role="alert">
          {text}
        </p>
      ))}
      {(["name", "email", "topic"] as const).map((field) => (
        <Field
          key={field}
          label={t(`inquiry.field.${field}`)}
          name={field}
          type={field === "email" ? "email" : "text"}
          autoComplete={field === "email" ? "email" : field === "name" ? "name" : undefined}
          value={form[field]}
          error={message(field)}
          onChange={(event) => {
            set(field)(event.target.value);
          }}
        />
      ))}
      <div className="md-field">
        <label htmlFor={messageId}>{t("inquiry.field.message")}</label>
        <textarea
          id={messageId}
          name="message"
          rows={6}
          className="md-field__input"
          value={form.message}
          aria-invalid={messageError ? true : undefined}
          aria-describedby={messageError ? `${messageId}-error` : undefined}
          onChange={(event) => {
            set("message")(event.target.value);
          }}
        />
        {messageError ? (
          <span id={`${messageId}-error`} className="md-field__error">
            {messageError}
          </span>
        ) : null}
      </div>
      {send.isError && violations.length === 0 ? (
        <p role="alert">{isApiError(send.error) ? send.error.message : t("error.unexpected")}</p>
      ) : null}
      <Button type="submit" disabled={send.isPending}>
        {t("inquiry.submit")}
      </Button>
    </form>
  );
}
