package com.pantrybase.api.common.config;

import com.pantrybase.api.auth.mail.PasswordResetMailer;
import com.pantrybase.api.auth.mail.SmtpPasswordResetMailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(JavaMailSender sender,
                                            @Value("${app.mail.from}") String from) {
        return new SmtpPasswordResetMailer(sender, from);
    }
}
