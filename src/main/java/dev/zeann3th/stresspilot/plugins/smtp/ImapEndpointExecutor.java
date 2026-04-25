package dev.zeann3th.stresspilot.plugins.smtp;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.mail.*;
import jakarta.mail.search.SubjectTerm;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Slf4j(topic = "ImapEndpointExecutor")
@Extension
public class ImapEndpointExecutor implements EndpointExecutor {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Override
    public String getType() {
        return "IMAP";
    }

    @Override
    public ExecuteEndpointResponse execute(
            EndpointEntity endpoint,
            Map<String, Object> environment,
            ExecutionContext context) {

        long startTime = System.currentTimeMillis();

        try {
            String[] hostPort = parseHostPort(endpoint.getUrl(), 993);
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            String security = endpoint.getHttpMethod() != null
                    ? endpoint.getHttpMethod().toUpperCase() : "SSL";

            Map<String, String> headers = parseHeaders(endpoint.getHttpHeaders(), environment);

            String username        = headers.getOrDefault("username", "");
            String password        = headers.getOrDefault("password", "");
            String folder          = headers.getOrDefault("folder", "INBOX");
            String subjectFilter   = headers.getOrDefault("subject_filter", "");
            long maxWaitMs         = parseLong(headers.get("max_wait_ms"), 10_000L);
            long pollIntervalMs    = parseLong(headers.get("poll_interval_ms"), 1_000L);
            boolean markSeen       = Boolean.parseBoolean(headers.getOrDefault("mark_seen", "false"));
            boolean deleteAfter    = Boolean.parseBoolean(headers.getOrDefault("delete_after", "false"));

            Properties props = buildProperties(host, port, security);
            Session session = Session.getInstance(props);
            Store store = session.getStore("imap");
            store.connect(host, port, username, password);

            Folder inbox = store.getFolder(folder);
            inbox.open(deleteAfter ? Folder.READ_WRITE : Folder.READ_ONLY);

            List<Message> matched = new ArrayList<>();
            long deadline = System.currentTimeMillis() + maxWaitMs;

            while (System.currentTimeMillis() < deadline) {
                Message[] messages = subjectFilter.isBlank()
                        ? inbox.getMessages()
                        : inbox.search(new SubjectTerm(subjectFilter));

                matched.addAll(Arrays.asList(messages));

                if (!matched.isEmpty()) break;
                Thread.sleep(pollIntervalMs);
            }

            boolean found = !matched.isEmpty();

            String subject = "";
            String bodySnippet = "";

            if (found) {
                Message first = matched.getFirst();
                subject = first.getSubject() != null ? first.getSubject() : "";
                bodySnippet = extractBodySnippet(first);

                if (markSeen) {
                    first.setFlag(Flags.Flag.SEEN, true);
                }
                if (deleteAfter) {
                    first.setFlag(Flags.Flag.DELETED, true);
                    inbox.expunge();
                }
            }

            inbox.close(deleteAfter);
            store.close();

            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> data = Map.of(
                    "found", found,
                    "count", matched.size(),
                    "subject", subject,
                    "body_snippet", bodySnippet,
                    "responseTimeMs", elapsed
            );

            return ExecuteEndpointResponse.builder()
                    .statusCode(found ? 200 : 404)
                    .success(found)
                    .responseTimeMs(elapsed)
                    .message(found
                            ? "Found " + matched.size() + " matching email(s)"
                            : "No matching email found within " + maxWaitMs + "ms")
                    .data(data)
                    .rawResponse(data.toString())
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute IMAP request for endpoint: {}", endpoint.getName(), e);
            return ExecuteEndpointResponse.builder()
                    .statusCode(500)
                    .success(false)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .message("IMAP Error: " + e.getMessage())
                    .build();
        }
    }

    // Same pattern as HttpEndpointExecutor.parseHeaders
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
            log.warn("Failed to parse IMAP headers: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Properties buildProperties(String host, int port, String security) {
        Properties props = new Properties();
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imap.connectiontimeout", "5000");
        props.put("mail.imap.timeout", "5000");

        switch (security) {
            case "SSL" -> {
                props.put("mail.imap.ssl.enable", "true");
                props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.imap.socketFactory.port", String.valueOf(port));
            }
            case "STARTTLS" -> props.put("mail.imap.starttls.enable", "true");
            // NONE — plain
        }
        return props;
    }

    private String extractBodySnippet(Message message) {
        try {
            Object content = message.getContent();
            String text = content instanceof String s ? s : content.toString();
            return text.length() > 500 ? text.substring(0, 500) + "..." : text;
        } catch (Exception e) {
            log.warn("Failed to extract IMAP body snippet: {}", e.getMessage());
            return "";
        }
    }

    private String[] parseHostPort(String url, int defaultPort) {
        if (url == null) return new String[]{"localhost", String.valueOf(defaultPort)};
        url = url.replaceFirst("^(imap://|imaps://)", "");
        String[] parts = url.split(":");
        return parts.length == 2
                ? parts
                : new String[]{parts[0], String.valueOf(defaultPort)};
    }

    private long parseLong(String value, long defaultVal) {
        if (value == null) return defaultVal;
        try { return Long.parseLong(value); } catch (NumberFormatException _) { return defaultVal; }
    }
}