package org.mobilecms.api.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.mobilecms.api.config.AppProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class MailService {

    private final AppProperties properties;
    private final JavaMailSender mailSender;

    public MailService(AppProperties properties, JavaMailSender mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    public String renderHtml(String subject, String password, String clientInfo, String date) {
        return template("mail/newpassword.html", subject, password, clientInfo, date);
    }

    public String renderText(String subject, String password, String clientInfo, String date) {
        return template("mail/newpassword.txt", subject, password, clientInfo, date);
    }

    public String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public void send(String from, String to, String title, String htmlBody, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(title);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Mailer Error: " + e.getMessage(), e);
        }
    }

    private String template(String path, String subject, String password, String clientInfo, String date) {
        try {
            String message = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            return message
                    .replace("%subject%", subject)
                    .replace("%password%", password)
                    .replace("%clientinfo%", clientInfo)
                    .replace("%currentdate%", date);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
