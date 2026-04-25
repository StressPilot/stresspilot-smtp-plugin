package dev.zeann3th.stresspilot.plugins.smtp;

import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.pf4j.Extension;

@Extension
public class SmtpEndpointValidator implements EndpointTypeValidator {

    @Override
    public boolean supports(String endpointType) {
        return "SMTP".equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        boolean valid = true;

        if (request.getUrl() == null || request.getUrl().isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "SMTP Plugin requires a host:port in url (e.g. smtp.gmail.com:587)"
            ).addConstraintViolation();
            valid = false;
        }

        if (request.getHttpHeaders() == null || request.getHttpHeaders().isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "SMTP Plugin requires headers: from, to, subject, username, password"
            ).addConstraintViolation();
            valid = false;
        }

        if (request.getBody() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "SMTP Plugin requires a body (email content)"
            ).addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}