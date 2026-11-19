package com.urlshortener.link;

import com.urlshortener.analytics.LinkClickedEvent;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.LinkProperties;
import com.urlshortener.link.LinkCache.CachedLink;
import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {

    private final ShortLinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final UrlPolicy urlPolicy;
    private final AliasPolicy aliasPolicy;
    private final AppProperties properties;
    private final LinkProperties linkProperties;
    private final LinkCache cache;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ShortLinkService(ShortLinkRepository repository, CodeGenerator codeGenerator,
            UrlPolicy urlPolicy, AliasPolicy aliasPolicy, AppProperties properties,
            LinkProperties linkProperties, LinkCache cache, Clock clock,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlPolicy = urlPolicy;
        this.aliasPolicy = aliasPolicy;
        this.properties = properties;
        this.linkProperties = linkProperties;
        this.cache = cache;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Creates a link owned by {@code ownerId}. Deliberately NOT transactional at this level: each
     * insert attempt runs in its own repository transaction, so a unique-constraint collision does
     * not poison an outer transaction and the retry loop can continue.
     */
    public LinkResponse create(CreateLinkRequest request, String ownerId) {
        String targetUrl = urlPolicy.validate(request.url());
        Instant now = clock.instant();
        Instant expiresAt = resolveExpiry(request.expiresAt(), now);

        if (request.alias() != null && !request.alias().isBlank()) {
            String alias = request.alias().trim();
            aliasPolicy.validate(alias);
            if (repository.existsByCode(alias)) {
                throw new AliasConflictException(alias); // common case, answered without an error log
            }
            try { // the unique constraint still guards the race between the check and the insert
                return toResponse(repository.saveAndFlush(
                        new ShortLink(alias, targetUrl, now, expiresAt, ownerId)));
            } catch (DataIntegrityViolationException e) {
                throw new AliasConflictException(alias);
            }
        }

        int attempts = properties.maxCodeGenerationAttempts();
        for (int i = 0; i < attempts; i++) {
            String code = codeGenerator.generate(properties.codeLength());
            try {
                return toResponse(repository.saveAndFlush(
                        new ShortLink(code, targetUrl, now, expiresAt, ownerId)));
            } catch (DataIntegrityViolationException collision) {
                // Code already taken; try another random code.
            }
        }
        throw new CodeGenerationException(attempts);
    }

    @Transactional(readOnly = true)
    public LinkResponse get(String code, String clientId) {
        return toResponse(findOwned(code, clientId));
    }

    /**
     * Resolves a code to its target URL and publishes a click event. The redirect path is
     * read-only and served from cache when possible; analytics are written asynchronously by
     * {@code ClickRecorder}, so a slow or failing analytics write cannot delay or break a redirect.
     */
    public String resolve(String code, String referrer, String userAgent) {
        CachedLink link = cache.get(code, this::load).orElseThrow(() -> new LinkNotFoundException(code));
        Instant now = clock.instant();
        if (!link.expiresAt().isAfter(now)) {
            throw new LinkExpiredException(code);
        }
        events.publishEvent(new LinkClickedEvent(link.id(), now, referrer, userAgent));
        return link.targetUrl();
    }

    @Transactional
    public void delete(String code, String clientId) {
        repository.delete(findOwned(code, clientId));
        cache.invalidate(code);
    }

    /** Removes links that expired more than {@code purgeAfter} ago. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(clock.instant().minus(linkProperties.purgeAfter()));
    }

    private Optional<CachedLink> load(String code) {
        return repository.findByCode(code)
                .map(l -> new CachedLink(l.getId(), l.getTargetUrl(), l.getExpiresAt()));
    }

    private Instant resolveExpiry(Instant requested, Instant now) {
        if (requested == null) {
            return now.plus(linkProperties.defaultTtl());
        }
        if (!requested.isAfter(now)) {
            throw new InvalidExpiryException("expiresAt must be in the future");
        }
        if (requested.isAfter(now.plus(linkProperties.maxTtl()))) {
            throw new InvalidExpiryException(
                    "expiresAt may be at most " + linkProperties.maxTtl().toDays() + " days ahead");
        }
        return requested;
    }

    /**
     * Another client's link is reported as not found (not 403) so callers cannot probe which
     * codes exist.
     */
    private ShortLink findOwned(String code, String clientId) {
        return repository.findByCode(code)
                .filter(link -> link.isOwnedBy(clientId))
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    private LinkResponse toResponse(ShortLink link) {
        return new LinkResponse(link.getCode(), shortUrl(link.getCode()), link.getTargetUrl(),
                link.getCreatedAt(), link.getExpiresAt(), link.getClickCount());
    }

    private String shortUrl(String code) {
        String base = properties.baseUrl();
        return base.endsWith("/") ? base + code : base + "/" + code;
    }
}
