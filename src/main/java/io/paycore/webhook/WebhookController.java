package io.paycore.webhook;

import io.paycore.common.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an endpoint. The signing secret is returned only in this response")
    public WebhookEndpointResponse register(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody CreateWebhookRequest request) {
        return webhookService.register(Principals.userId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "List the current user's webhook endpoints")
    public List<WebhookEndpointResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return webhookService.list(Principals.userId(jwt));
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an endpoint and its pending deliveries")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID endpointId) {
        webhookService.delete(Principals.userId(jwt), endpointId);
    }

    @GetMapping("/{endpointId}/deliveries")
    @Operation(summary = "Recent delivery attempts for an endpoint, newest first")
    public List<WebhookDeliveryResponse> deliveries(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID endpointId,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return webhookService.deliveries(Principals.userId(jwt), endpointId, limit);
    }

    @PostMapping("/{endpointId}/deliveries/{deliveryId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Re-queue a DEAD delivery")
    public void retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID endpointId, @PathVariable UUID deliveryId) {
        webhookService.retry(Principals.userId(jwt), endpointId, deliveryId);
    }
}
