package com.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    record DayCount(LocalDate day, Long clicks) {
    }

    record KeyCount(String key, Long clicks) {
    }

    @Query("""
            select new com.urlshortener.analytics.ClickEventRepository$DayCount(c.clickedOn, count(c))
            from ClickEvent c
            where c.linkId = :linkId and c.clickedOn >= :from
            group by c.clickedOn
            order by c.clickedOn""")
    List<DayCount> countByDay(@Param("linkId") long linkId, @Param("from") LocalDate from);

    @Query("""
            select new com.urlshortener.analytics.ClickEventRepository$KeyCount(c.referrerHost, count(c))
            from ClickEvent c
            where c.linkId = :linkId and c.clickedOn >= :from and c.referrerHost is not null
            group by c.referrerHost
            order by count(c) desc, c.referrerHost""")
    List<KeyCount> topReferrers(@Param("linkId") long linkId, @Param("from") LocalDate from,
            Pageable page);

    @Query("""
            select new com.urlshortener.analytics.ClickEventRepository$KeyCount(c.agentFamily, count(c))
            from ClickEvent c
            where c.linkId = :linkId and c.clickedOn >= :from
            group by c.agentFamily
            order by count(c) desc, c.agentFamily""")
    List<KeyCount> countByAgentFamily(@Param("linkId") long linkId, @Param("from") LocalDate from);
}
