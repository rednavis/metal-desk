package com.rednavis.metaldesk.api.web;

import java.io.Serializable;

/**
 * One thing wrong with one field of a request.
 *
 * @param field the name of the field as the client sent it, with a dot for a nested one
 * @param code the machine-readable code, such as {@code required} or {@code format}
 * @param message a human-readable description
 */
public record FieldViolation(String field, String code, String message) implements Serializable {}
