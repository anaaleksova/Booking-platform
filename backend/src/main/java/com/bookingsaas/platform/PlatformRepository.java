package com.bookingsaas.platform;

import com.bookingsaas.platform.PlatformDtos.AccountRow;
import com.bookingsaas.platform.PlatformDtos.ActivityRow;
import com.bookingsaas.platform.PlatformDtos.BusinessRow;
import com.bookingsaas.platform.PlatformDtos.Overview;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Read-mostly queries across every business, in plain SQL: the one place in
 * the application that is deliberately not scoped to a tenant.
 */
@Repository
public class PlatformRepository {

    /** Lists stop here; search narrows them. */
    static final int LIST_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Overview overview() {
        return jdbc.queryForObject("""
                select (select count(*) from business)                                        as businesses,
                       (select count(*) from business where active)                           as active_businesses,
                       (select count(*) from app_user)                                        as accounts,
                       (select count(*) from reservation
                         where created_at >= date_trunc('month', now() at time zone 'utc') at time zone 'utc')
                                                                                              as reservations_this_month,
                       (select count(*) from business where created_at >= now() - interval '30 days')
                                                                                              as new_businesses
                """, new MapSqlParameterSource(), (rs, row) -> new Overview(rs.getLong("businesses"),
                rs.getLong("active_businesses"), rs.getLong("accounts"), rs.getLong("reservations_this_month"),
                rs.getLong("new_businesses")));
    }

    /** Newest first; {@code query} matches the name, the page address or an owner's email. */
    public List<BusinessRow> businesses(String query) {
        return jdbc.query("""
                select b.id, b.name, b.slug, b.active, b.created_at,
                       array(select u.email from business_user bu join app_user u on u.id = bu.user_id
                             where bu.business_id = b.id and bu.role = 'BUSINESS_OWNER' order by bu.id) as owners,
                       (select count(*) from reservation r
                         where r.business_id = b.id and r.created_at >= now() - interval '30 days') as recent
                from business b
                where :query = ''
                   or b.name ilike :pattern or b.slug ilike :pattern
                   or exists (select 1 from business_user bu join app_user u on u.id = bu.user_id
                              where bu.business_id = b.id and u.email ilike :pattern)
                order by b.created_at desc, b.id desc
                limit :limit
                """, search(query), (rs, row) -> new BusinessRow(rs.getLong("id"), rs.getString("name"),
                rs.getString("slug"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(),
                strings(rs, "owners"), rs.getLong("recent")));
    }

    /** Newest first; {@code query} matches the email or the name. Platform admins are marked by the caller. */
    public List<AccountRow> accounts(String query) {
        return jdbc.query("""
                select u.id, u.email, u.full_name, u.active, u.created_at,
                       array(select case bu.role when 'BUSINESS_OWNER' then 'Owner' else 'Staff' end || ' · ' || b.name
                             from business_user bu join business b on b.id = bu.business_id
                             where bu.user_id = u.id order by bu.id) as roles,
                       (select count(*) from customer c where lower(c.email) = u.email) as customer_at
                from app_user u
                where :query = '' or u.email ilike :pattern or u.full_name ilike :pattern
                order by u.created_at desc, u.id desc
                limit :limit
                """, search(query), (rs, row) -> new AccountRow(rs.getLong("id"), rs.getString("email"),
                rs.getString("full_name"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(),
                strings(rs, "roles"), rs.getLong("customer_at"), false));
    }

    public void recordActivity(Long actorUserId, String actorEmail, String action, String targetType, Long targetId,
                               String targetName) {
        jdbc.update("""
                insert into platform_activity (actor_user_id, actor_email, action, target_type, target_id, target_name)
                values (:actorUserId, :actorEmail, :action, :targetType, :targetId, :targetName)
                """, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("actorEmail", actorEmail)
                .addValue("action", action)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId)
                .addValue("targetName", targetName));
    }

    public List<ActivityRow> activity() {
        return jdbc.query("""
                select id, actor_email, action, target_type, target_id, target_name, created_at
                from platform_activity order by created_at desc, id desc limit :limit
                """, new MapSqlParameterSource("limit", LIST_LIMIT), (rs, row) -> new ActivityRow(rs.getLong("id"),
                rs.getString("actor_email"), rs.getString("action"), rs.getString("target_type"),
                rs.getLong("target_id"), rs.getString("target_name"), rs.getTimestamp("created_at").toInstant()));
    }

    private static MapSqlParameterSource search(String query) {
        String trimmed = query == null ? "" : query.trim();
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new MapSqlParameterSource()
                .addValue("query", trimmed)
                .addValue("pattern", "%" + escaped + "%")
                .addValue("limit", LIST_LIMIT);
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
