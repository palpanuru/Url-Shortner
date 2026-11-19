package com.urlshortener.link;

import com.urlshortener.analytics.LinkClickedEvent;
import com.urlshortener.config.AppProperties;
import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ShortLinkService(ShortLinkRepository repository, CodeGenerator codeGenerator,
            UrlPolicy urlPolicy, AliasPolicy aliasPolicy, AppProperties properties, Clock clock,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlPolicy = urlPolicy;
        this.aliasPolicy = aliasPolicy;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Creates a link. Deliberately NOT transactional at this level: each insert attempt runs in
     * its own repository transaction, so a unique-constraint collision does not poison an outer
     * transaction and the retry loop can continue.
     */
    public LinkResponse create(CreateLinkRequest request) {
        String targetUrl = urlPolicy.validate(request.url());
        Instant now = clock.instant();

        if (request.alias() != null && !request.alias().isBlank()) {
            String alias = request.alias().trim();
            aliasPolicy.validate(alias);
            try {
                return toResponse(repository.saveAndFlush(new ShortLink(alias, targetUrl, now)));
            } catch (DataIntegrityViolationException e) {
                throw new AliasConflictException(alias);
            }
        }

        int attempts = properties.maxCodeGenerationAttempts();
        for (int i = 0; i < attempts; i++) {
            String code = codeGenerator.generate(properties.codeLength());
            try {
                return toResponse(repository.saveAndFlush(new ShortLink(code, targetUrl, now)));
            } catch (DataIntegrityViolationException collision) {
                // Code already taken; try another random code.
            }
        }
        throw new CodeGenerationException(attempts);
    }

    @Transactional(readOnly = true)
    public LinkResponse get(String code) {
        return toResponse(find(code));
    }

    /**
     * Resolves a code to its target URL and publishes a click event. The redirect path is now
     * read-only: analytics are written asynchronously by {@code ClickRecorder}, so a slow or
     * failing analytics write cannot delay or break a redirect.
     */
    @Transactional(readOnly = true)
    public String resolve(String code, String referrer, String userAgent) {
        ShortLink link = find(code);
        events.publishEvent(
                new LinkClickedEvent(link.getId(), clock.instant(), referrer, userAgent));
        return link.getTargetUrl();
    }

    @Transactional
    public void delete(String code) {
        repository.delete(find(code));
    }

    private ShortLink find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    private LinkResponse toResponse(ShortLink link) {
        return new LinkResponse(link.getCode(), shortUrl(link.getCode()), link.getTargetUrl(),
                link.getCreatedAt(), link.getClickCount());
    }

    private String shortUrl(String code) {
        String base = properties.baseUrl();
        return base.endsWith("/") ? base + code : base + "/" + code;
    }
}
