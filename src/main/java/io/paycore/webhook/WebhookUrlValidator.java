package io.paycore.webhook;

import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Blocks the obvious SSRF targets (localhost, private networks, cloud metadata) unless private hosts are allowed,
 * which is only meant for local development and tests.
 */
@Component
class WebhookUrlValidator {

    private final WebhookProperties properties;

    WebhookUrlValidator(WebhookProperties properties) {
        this.properties = properties;
    }

    String validate(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw invalid("URL is malformed");
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null) {
            throw invalid("URL must be absolute");
        }
        if (properties.allowPrivateHosts()) {
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw invalid("URL must use http or https");
            }
            return uri.toString();
        }
        if (!scheme.equals("https")) {
            throw invalid("URL must use https");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw invalid("URL must not point to a private or local address");
                }
            }
        } catch (UnknownHostException e) {
            throw invalid("Host cannot be resolved");
        }
        return uri.toString();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_WEBHOOK_URL, message);
    }
}
