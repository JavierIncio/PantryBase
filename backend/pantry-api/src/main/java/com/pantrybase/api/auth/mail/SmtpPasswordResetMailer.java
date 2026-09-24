package com.pantrybase.api.auth.mail;

import com.pantrybase.api.common.exception.SmtpException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SmtpPasswordResetMailer implements PasswordResetMailer {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        try {
            ClassPathResource resource = new ClassPathResource("email/password-reset.html");
            String html = StreamUtils
                    .copyToString(resource.getInputStream(), StandardCharsets.UTF_8)
                    .replace("{{NAME}}", name)
                    .replace("{{RESET_LINK}}", resetLink);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("PantryBase - Password Reset");
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException | IOException e) {
            throw new SmtpException();
        }
    }
}
