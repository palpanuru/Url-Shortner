package com.urlshortener.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "clicked_on", nullable = false)
    private LocalDate clickedOn;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "agent_family", nullable = false, length = 32)
    private String agentFamily;

    protected ClickEvent() {
        // for JPA
    }

    public ClickEvent(Long linkId, Instant clickedAt, LocalDate clickedOn, String referrerHost,
            String agentFamily) {
        this.linkId = linkId;
        this.clickedAt = clickedAt;
        this.clickedOn = clickedOn;
        this.referrerHost = referrerHost;
        this.agentFamily = agentFamily;
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public LocalDate getClickedOn() {
        return clickedOn;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public String getAgentFamily() {
        return agentFamily;
    }
}
