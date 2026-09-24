package com.pantrybase.api.auth.mail;

public interface PasswordResetMailer {
    /** Delivers the password reset email for an existing account. */
    void sendPasswordResetEmail(String to, String name, String resetLink);
}
