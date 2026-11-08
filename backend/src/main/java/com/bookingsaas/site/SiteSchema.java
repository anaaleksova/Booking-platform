package com.bookingsaas.site;

import com.bookingsaas.site.model.SiteDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Keeps stored homepages readable as the format evolves.
 *
 * <p>Every change to the document format that old data would not parse
 * under bumps the version and adds one step to {@link #STEPS}, turning a
 * document of the previous version into the next. A stored document is run
 * through the steps it is behind on every read, before it is bound, and is
 * written back in the new version the next time it is saved.
 */
@Component
public class SiteSchema {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * v1 → v2: the theme gained typography and component styling. A version 1
     * page gets the defaults, which are how it looked before they existed.
     */
    private static final UnaryOperator<ObjectNode> ADD_TYPOGRAPHY_AND_COMPONENTS = document -> {
        ObjectNode theme = document.with("theme");
        if (!theme.has("typography")) {
            theme.set("typography", JSON.valueToTree(SiteDocument.Typography.DEFAULT));
        }
        if (!theme.has("components")) {
            theme.set("components", JSON.valueToTree(SiteDocument.Components.DEFAULT));
        }
        return document;
    };

    /** {@code STEPS.get(i)} upgrades version {@code i + 1} to {@code i + 2}. */
    private static final List<UnaryOperator<ObjectNode>> STEPS = List.of(ADD_TYPOGRAPHY_AND_COMPONENTS);

    public static final int CURRENT_VERSION = STEPS.size() + 1;

    private final List<UnaryOperator<ObjectNode>> steps;

    public SiteSchema() {
        this(STEPS);
    }

    SiteSchema(List<UnaryOperator<ObjectNode>> steps) {
        this.steps = steps;
    }

    public int currentVersion() {
        return steps.size() + 1;
    }

    /**
     * The document in the current version. A document without a version is
     * version 1. One from a newer version than this server knows (a rollback
     * after a deploy) is refused rather than half-read.
     */
    public ObjectNode upgrade(JsonNode stored) {
        if (!(stored instanceof ObjectNode document)) {
            throw new IllegalStateException("A site document must be a JSON object");
        }
        int version = document.path("schemaVersion").asInt(1);
        if (version > currentVersion()) {
            throw new IllegalStateException("Site document version " + version + " is newer than this server ("
                    + currentVersion() + ")");
        }
        ObjectNode current = document.deepCopy();
        for (int step = version; step < currentVersion(); step++) {
            current = steps.get(step - 1).apply(current);
            current.put("schemaVersion", step + 1);
        }
        current.put("schemaVersion", currentVersion());
        return current;
    }
}
