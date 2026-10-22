package com.bookingsaas.customer.migration;

import com.bookingsaas.common.validation.Phones;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Brings every stored customer phone number into one form and merges the
 * customers that turn out to share a number.
 *
 * <p>One phone number is one person. Before numbers were normalised, the
 * same person could be stored twice — "+38970555444" from an Instagram chat,
 * "+389 70 555 444" from the booking page, "070 555 444" typed in the admin.
 * Per business, each number is read in that business's country (from its own
 * phone number), and all customers with the same number become one:
 *
 * <ul>
 *   <li>the oldest record survives;</li>
 *   <li>the others' reservations, Instagram/Facebook links and chats move to it;</li>
 *   <li>details it lacks (email, last name, notes) are taken from the others;
 *       a second, different email is kept in the notes, so nothing is lost;</li>
 *   <li>the others are deleted.</li>
 * </ul>
 *
 * <p>Plain JDBC on purpose: it runs inside the Liquibase changeset, before
 * the application (and Hibernate) starts.
 */
public class MergeCustomersByPhone implements CustomTaskChange {

    /** What the migration did. */
    public record Result(int normalized, int merged) {
    }

    private Result result;

    @Override
    public void execute(Database database) throws CustomChangeException {
        try {
            result = run(((JdbcConnection) database.getConnection()).getUnderlyingConnection());
        } catch (SQLException ex) {
            throw new CustomChangeException("Could not normalise customer phone numbers", ex);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return result == null ? "Customer phone numbers normalised"
                : "Customer phone numbers normalised: " + result.normalized() + " rewritten, "
                        + result.merged() + " duplicate customers merged";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }

    private record Row(long id, long businessId, String phone, String email, String lastName, String notes) {
    }

    /** The whole migration, on one connection (and so in the caller's transaction). */
    public static Result run(Connection connection) throws SQLException {
        Map<Long, String> regions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, phone FROM business");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                regions.put(rows.getLong("id"), Phones.regionOf(rows.getString("phone")));
            }
        }

        // business → normalised number → its customers, oldest first
        Map<Long, Map<String, List<Row>>> byNumber = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, business_id, phone, email, last_name, notes FROM customer
                WHERE phone IS NOT NULL ORDER BY business_id, id""");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                Row row = new Row(rows.getLong("id"), rows.getLong("business_id"), rows.getString("phone"),
                        rows.getString("email"), rows.getString("last_name"), rows.getString("notes"));
                String number = Phones.normalize(row.phone(), regions.get(row.businessId()));
                byNumber.computeIfAbsent(row.businessId(), key -> new LinkedHashMap<>())
                        .computeIfAbsent(number, key -> new ArrayList<>()).add(row);
            }
        }

        int normalized = 0;
        int merged = 0;
        for (Map<String, List<Row>> numbers : byNumber.values()) {
            for (Map.Entry<String, List<Row>> entry : numbers.entrySet()) {
                List<Row> same = entry.getValue();
                Row survivor = same.getFirst();
                String email = survivor.email();
                String lastName = survivor.lastName();
                String notes = survivor.notes();
                for (Row duplicate : same.subList(1, same.size())) {
                    moveEverything(connection, duplicate.id(), survivor.id());
                    if (isBlank(email)) {
                        email = duplicate.email();
                    } else if (!isBlank(duplicate.email()) && !email.equalsIgnoreCase(duplicate.email())) {
                        notes = appendLine(notes, "Also used the email " + duplicate.email() + ".");
                    }
                    if (isBlank(lastName)) {
                        lastName = duplicate.lastName();
                    }
                    if (!isBlank(duplicate.notes())) {
                        notes = appendLine(notes, duplicate.notes());
                    }
                    merged++;
                }
                boolean changed = !entry.getKey().equals(survivor.phone()) || same.size() > 1;
                if (changed) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE customer SET phone = ?, email = ?, last_name = ?, notes = ?, updated_at = now()
                            WHERE id = ?""")) {
                        update.setString(1, entry.getKey());
                        update.setString(2, email);
                        update.setString(3, Objects.requireNonNullElse(lastName, ""));
                        update.setString(4, notes);
                        update.setLong(5, survivor.id());
                        update.executeUpdate();
                    }
                    normalized++;
                }
            }
        }
        return new Result(normalized, merged);
    }

    /** Hands everything of {@code from} to {@code to}, then deletes {@code from}. */
    private static void moveEverything(Connection connection, long from, long to) throws SQLException {
        for (String sql : List.of(
                "UPDATE reservation SET customer_id = ? WHERE customer_id = ?",
                "UPDATE customer_external_identity SET customer_id = ? WHERE customer_id = ?",
                "UPDATE integration_conversation SET customer_id = ? WHERE customer_id = ?")) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, to);
                statement.setLong(2, from);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM customer WHERE id = ?")) {
            statement.setLong(1, from);
            statement.executeUpdate();
        }
    }

    private static String appendLine(String text, String line) {
        return isBlank(text) ? line : text + "\n" + line;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
