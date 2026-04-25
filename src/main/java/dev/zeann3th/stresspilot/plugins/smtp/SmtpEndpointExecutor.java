package dev.zeann3th.stresspilot.plugins.smtp;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j(topic = "SmtpEndpointExecutor")
@Extension
public class SmtpEndpointExecutor implements EndpointExecutor {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Override
    public String getType() {
        return "SMTP";
    }

    @Override
    public ExecuteEndpointResponse execute(
            EndpointEntity endpoint,
            Map<String, Object> environment,
            ExecutionContext context) {

        long startTime = System.currentTimeMillis();

        try {
            String[] hostPort = parseHostPort(endpoint.getUrl(), 587);
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            String security = endpoint.getHttpMethod() != null
                    ? endpoint.getHttpMethod().toUpperCase() : "STARTTLS";

            Map<String, String> headers = parseHeaders(endpoint.getHttpHeaders(), environment);

            String from        = headers.getOrDefault("from", "");
            String username    = headers.getOrDefault("username", "");
            String password    = headers.getOrDefault("password", "");
            String to          = headers.getOrDefault("to", "");
            String subject     = headers.getOrDefault("subject", "");
            String contentType = headers.getOrDefault("content_type", "text/plain");

            String body = parseBody(endpoint.getBody(), environment);

            Properties props = buildProperties(host, port, security);
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(body, contentType + "; charset=utf-8");

            Transport.send(message);

            long elapsed = System.currentTimeMillis() - startTime;

            return ExecuteEndpointResponse.builder()
                    .statusCode(200)
                    .success(true)
                    .responseTimeMs(elapsed)
                    .message("Email sent to " + to)
                    .data(Map.of(
                            "to", to,
                            "subject", subject,
                            "from", from,
                            "responseTimeMs", elapsed
                    ))
                    .rawResponse("SMTP 250 OK")
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute SMTP request for endpoint: {}", endpoint.getName(), e);
            return ExecuteEndpointResponse.builder()
                    .statusCode(500)
                    .success(false)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .message("SMTP Error: " + e.getMessage())
                    .build();
        }
    }

    private Map<String, String> parseHeaders(String headersJson, Map<String, Object> environment) {
        if (headersJson == null || headersJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, String> rawHeaders = jsonMapper.readValue(headersJson, new TypeReference<>() {});
            Map<String, String> processedHeaders = new HashMap<>();

            rawHeaders.forEach((key, value) -> {
                String processedValue = value;
                if (processedValue.contains("{{")) {
                    processedValue = DataUtils.replaceVariables(processedValue, environment);
                }
                if (processedValue.contains("@{")) {
                    processedValue = MockDataUtils.interpolate(processedValue);
                }
                processedHeaders.put(key, processedValue);
            });

            return processedHeaders;
        } catch (Exception e) {
            log.warn("Failed to parse SMTP headers: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // Same pattern as HttpEndpointExecutor.parseBody — returns String instead of RequestBody
    private String parseBody(String rawBody, Map<String, Object> environment) {
        if (rawBody == null) return "";

        String processedBody = rawBody;
        if (processedBody.contains("{{")) {
            processedBody = DataUtils.replaceVariables(processedBody, environment);
        }
        if (processedBody.contains("@{")) {
            processedBody = MockDataUtils.interpolate(processedBody);
        }
        log.debug("SMTP body after processing: {}", processedBody);
        return processedBody;
    }

    private Properties buildProperties(String host, int port, String security) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        switch (security) {
            case "SSL" -> {
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.ssl.enable", "true");
            }
            case "NONE" -> {
                // plain, no TLS
            }
            default -> {
                // STARTTLS
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }
        return props;
    }

    private String[] parseHostPort(String url, int defaultPort) {
        if (url == null) return new String[]{"localhost", String.valueOf(defaultPort)};
        url = url.replaceFirst("^(smtp://|smtps://)", "");
        String[] parts = url.split(":");
        return parts.length == 2
                ? parts
                : new String[]{parts[0], String.valueOf(defaultPort)};
    }
}