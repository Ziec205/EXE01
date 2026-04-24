package com.thatrico.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class MailService {
    public String sendOtp(String to, String name, String otp) {
        String host = env("SMTP_HOST");
        String user = env("SMTP_USER");
        String pass = env("SMTP_PASS");
        String port = env("SMTP_PORT");

        if (isBlank(host) || isBlank(user) || isBlank(pass)) {
            System.out.println("[OTP DEV] " + to + ": " + otp);
            return otp;
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(isBlank(port) ? 587 : Integer.parseInt(port));
        mailSender.setUsername(user);
        mailSender.setPassword(pass);

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(envOr("OTP_FROM", user));
        message.setTo(to);
        message.setSubject("Thatrico - Ma OTP xac thuc dang ky");
        message.setText(String.join("\n",
                "Xin chao " + (name == null || name.isBlank() ? "ban" : name) + ",",
                "",
                "Ma OTP xac thuc cua ban la: " + otp,
                "Ma nay co hieu luc trong 10 phut.",
                "",
                "Neu ban khong yeu cau ma nay, hay bo qua email nay."
        ));
        mailSender.send(message);
        return null;
    }

    private String env(String key) {
        return System.getenv(key);
    }

    private String envOr(String key, String fallback) {
        String value = env(key);
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
