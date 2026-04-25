package dev.zeann3th.stresspilot.plugins.smtp;

import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.pf4j.Extension;

@Extension
public class ImapEndpointValidator implements EndpointTypeValidator {

    @Override
    public boolean supports(String endpointType) {
        return "IMAP".equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        boolean valid = true;

        if (request.getUrl() == null || request.getUrl().isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "IMAP Plugin requires a host:port in url (e.g. imap.gmail.com:993)"
            ).addConstraintViolation();
            valid = false;
        }

        if (request.getHttpHeaders() == null || request.getHttpHeaders().isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "IMAP Plugin requires headers: username, password"
            ).addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}