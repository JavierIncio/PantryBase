package com.pantrybase.api.common.exception;

public class SmtpException extends RuntimeException {
    public SmtpException() {
        super("Failed to send email via SMTP.");
    }
}
