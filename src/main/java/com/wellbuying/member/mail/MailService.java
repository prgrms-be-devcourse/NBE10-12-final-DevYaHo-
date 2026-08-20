package com.wellbuying.member.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String FROM_DISPLAY_NAME = "Wellbuying";

    private static final String LAYOUT_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ko">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Apple SD Gothic Neo',Arial,sans-serif;">
              <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:40px 0;">
                <tr>
                  <td align="center">
                    <table width="520" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                      <tr>
                        <td style="background-color:#4A90E2;padding:32px 40px;text-align:center;">
                          <h1 style="margin:0;color:#ffffff;font-size:26px;font-weight:700;letter-spacing:-0.5px;">Wellbuying</h1>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:40px 40px 32px;">
                          {{CONTENT}}
                        </td>
                      </tr>
                      <tr>
                        <td style="background-color:#f9f9f9;padding:20px 40px;text-align:center;border-top:1px solid #eeeeee;">
                          <p style="margin:0;color:#cccccc;font-size:12px;">&copy; 2026 Wellbuying. All rights reserved.</p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    // 비동기(mailExecutor 풀)로 HTML 이메일 발송, 실패해도 예외를 호출자에 전파하지 않고 로깅만 함
    @Async("mailExecutor")
    public void sendHtmlEmail(String to, String subject, String contentHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, FROM_DISPLAY_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(wrapWithLayout(contentHtml), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("mail send failed to={}", to, e);
        }
    }

    private String wrapWithLayout(String contentHtml) {
        return LAYOUT_TEMPLATE.replace("{{CONTENT}}", contentHtml);
    }
}
