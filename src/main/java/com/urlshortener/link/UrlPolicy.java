package com.urlshortener.link;

import com.urlshortener.config.AppProperties;
import com.urlshortener.config.LinkProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides whether a target URL is acceptable. Kept separate from the service so the rules can
 * evolve (and be tested) independently of persistence.
 *
 * <p>Note: the service never fetches target URLs, so this is not SSRF protection. The private
 * address rules stop the shortener being used to hand out links into internal networks, and the
 * remaining rules block common phishing and redirect-loop tricks. Hostnames are deliberately NOT
 * resolved via DNS: that would add latency, an external dependency, and still be bypassable by
 * DNS rebinding.
 *
 * <p>Final because the constructor can throw on a bad base URL (SpotBugs CT_CONSTRUCTOR_THROW).
 */
@Component
public final class UrlPolicy {

    static final int MAX_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    /** Hosts such as "2130706433" or "0x7f000001" that browsers treat as an IPv4 address. */
    private static final Pattern NUMERIC_HOST = Pattern.compile("^(0x[0-9a-f]+|\\d+)$");

    private final String ownHost;
    private final boolean blockPrivateAddresses;
    private final List<String> blockedDomains;

    public UrlPolicy(AppProperties app, LinkProperties links) {
        this.ownHost = hostOf(app.baseUrl());
        this.blockPrivateAddresses = links.blockPrivateAddresses();
        this.blockedDomains = links.blockedDomains().stream()
                .map(d -> d.toLowerCase(Locale.ROOT).trim())
                .filter(d -> !d.isEmpty())
                .toList();
    }

    /** Validates and returns the normalized URL to store. */
    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }
        String url = rawUrl.trim();
        if (url.length() > MAX_LENGTH) {
            throw new InvalidUrlException("URL exceeds " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not well formed");
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must include a host");
        }
        if (uri.getRawUserInfo() != null) {
            // https://trusted.com@evil.com/ is a classic phishing disguise.
            throw new InvalidUrlException("URLs with embedded credentials are not allowed");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals(ownHost)) {
            throw new InvalidUrlException("URL must not point back at this shortener");
        }
        if (isBlockedDomain(host)) {
            throw new InvalidUrlException("Domain is not allowed");
        }
        if (blockPrivateAddresses && isPrivateOrLocal(host)) {
            throw new InvalidUrlException("URLs to local or private network addresses are not allowed");
        }
        return url;
    }

    private boolean isBlockedDomain(String host) {
        for (String domain : blockedDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPrivateOrLocal(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            return true;
        }
        if (NUMERIC_HOST.matcher(host).matches()) {
            return true;
        }
        boolean ipv6 = host.startsWith("[") && host.endsWith("]");
        if (!ipv6 && !IPV4_LITERAL.matcher(host).matches()) {
            return false; // a DNS name; not resolved by design
        }
        try {
            // Never triggers DNS: IPv4 is built from parsed octets, and a bracketed IPv6 literal
            // makes getByName fail rather than fall back to a lookup.
            InetAddress address = ipv6 ? InetAddress.getByName(host) : ipv4(host);
            return address == null || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || isUniqueLocalIpv6(address) || isCarrierGradeNat(address);
        } catch (UnknownHostException e) {
            return true; // unparseable literal: fail closed
        }
    }

    /** Parses a dotted-quad literal; returns null (treated as blocked) if any octet is invalid. */
    private static InetAddress ipv4(String host) throws UnknownHostException {
        String[] parts = host.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet > 255) {
                return null;
            }
            bytes[i] = (byte) octet;
        }
        return InetAddress.getByAddress(bytes);
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // fc00::/7
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10
    }

    private static String hostOf(String baseUrl) {
        try {
            String host = new URI(baseUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("app.base-url is not a valid URL: " + baseUrl, e);
        }
    }
}
