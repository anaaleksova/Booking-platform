package com.bookingsaas.event.share;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.event.BusinessEvent;
import com.bookingsaas.event.BusinessEventRepository;
import com.bookingsaas.event.share.EventShareDtos.PostView;
import com.bookingsaas.event.share.EventShareDtos.SharePreview;
import com.bookingsaas.event.share.EventShareDtos.ShareRequest;
import com.bookingsaas.event.share.EventShareDtos.ShareResult;
import com.bookingsaas.event.share.EventSocialPost.Platform;
import com.bookingsaas.integration.meta.MetaConnection;
import com.bookingsaas.integration.meta.MetaConnectionRepository;
import com.bookingsaas.integration.meta.MetaGraphClient;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaGraphException;
import com.bookingsaas.integration.meta.MetaGraphClient.PublishedPost;
import com.bookingsaas.integration.meta.MetaSettings;
import com.bookingsaas.integration.meta.TokenCipher;
import com.bookingsaas.media.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Shares an event on the business's connected Facebook Page and Instagram
 * account, with a caption the owner reviews first. Each platform succeeds or
 * fails on its own, and every attempt is kept so the owner sees what went out.
 *
 * <p>Meta is called outside any database transaction.
 */
@Service
public class EventShareService {

    private static final Logger log = LoggerFactory.getLogger(EventShareService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DESCRIPTION_IN_CAPTION = 1200;

    private final BusinessEventRepository events;
    private final EventSocialPostRepository posts;
    private final MetaConnectionRepository connections;
    private final MetaGraphClient graph;
    private final MetaSettings settings;
    private final TokenCipher cipher;
    private final MediaService mediaService;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public EventShareService(BusinessEventRepository events, EventSocialPostRepository posts,
                             MetaConnectionRepository connections, MetaGraphClient graph, MetaSettings settings,
                             TokenCipher cipher, MediaService mediaService, BusinessService businessService,
                             CurrentTenant currentTenant, Clock clock, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.posts = posts;
        this.connections = connections;
        this.graph = graph;
        this.settings = settings;
        this.cipher = cipher;
        this.mediaService = mediaService;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Where each of the business's events has been posted, for the events list. */
    public Map<Long, Set<String>> sharedOn(Long businessId) {
        return posts.findByBusinessIdAndStatus(businessId, EventSocialPost.Status.POSTED).stream()
                .collect(Collectors.groupingBy(EventSocialPost::getEventId,
                        Collectors.mapping(post -> post.getPlatform().name(), Collectors.toSet())));
    }

    public SharePreview preview(Long eventId) {
        currentTenant.requireOwner();
        Context context = Objects.requireNonNull(transaction.execute(status -> load(eventId)));
        MetaConnection connection = context.connection();
        List<PostView> history = transaction.execute(status -> posts.findByEventIdOrderByCreatedAtDesc(eventId)
                .stream().map(PostView::of).toList());
        return new SharePreview(connection == null ? null : connection.getPageName(),
                connection == null ? null : connection.getInstagramUsername(),
                facebookUnavailable(context), instagramUnavailable(context), caption(context), history);
    }

    public ShareResult share(Long eventId, ShareRequest request) {
        currentTenant.requireOwner();
        if (!request.facebook() && !request.instagram()) {
            throw ApiException.validation("Choose Facebook, Instagram or both.");
        }
        Context context = Objects.requireNonNull(transaction.execute(status -> load(eventId)));
        if (request.facebook() && facebookUnavailable(context) != null) {
            throw ApiException.validation(facebookUnavailable(context));
        }
        if (request.instagram() && instagramUnavailable(context) != null) {
            throw ApiException.validation(instagramUnavailable(context));
        }
        MetaConnection connection = context.connection();
        String token;
        try {
            token = cipher.decrypt(connection.getPageTokenEncrypted());
        } catch (IllegalStateException ex) {
            throw ApiException.validation("Your Facebook connection can no longer be read. Connect again on Integrations.");
        }
        String caption = request.caption().trim();
        Optional<String> picture = pictureUrl(context);

        List<EventSocialPost> results = new ArrayList<>();
        if (request.facebook()) {
            results.add(attempt(context, Platform.FACEBOOK, () -> picture.isPresent()
                    ? graph.postPagePhoto(connection.getPageId(), token, picture.get(), caption)
                    : graph.postPageText(connection.getPageId(), token, caption)));
        }
        if (request.instagram()) {
            results.add(attempt(context, Platform.INSTAGRAM, () -> graph.postInstagramImage(
                    connection.getInstagramAccountId(), token, picture.orElseThrow(), caption)));
        }
        List<EventSocialPost> saved = transaction.execute(status -> posts.saveAll(results));
        return new ShareResult(Objects.requireNonNull(saved).stream().map(PostView::of).toList());
    }

    // --- helpers -----------------------------------------------------------

    private record Context(BusinessEvent event, Business business, MetaConnection connection, String imageType) {
    }

    private Context load(Long eventId) {
        Long businessId = currentTenant.businessId();
        BusinessEvent event = events.findByIdAndBusinessId(eventId, businessId)
                .orElseThrow(() -> ApiException.notFound("Event", eventId));
        String imageType = event.getImageMediaId() == null ? null
                : mediaService.require(event.getImageMediaId()).getContentType();
        return new Context(event, businessService.requireById(businessId),
                connections.findByBusinessId(businessId).orElse(null), imageType);
    }

    private EventSocialPost attempt(Context context, Platform platform, Supplier<PublishedPost> call) {
        Long eventId = context.event().getId();
        Long businessId = context.business().getId();
        try {
            PublishedPost post = call.get();
            log.info("Event {} of business {} posted on {} ({})", eventId, businessId, platform, post.id());
            return EventSocialPost.posted(eventId, businessId, platform, post.id(), post.permalink(), clock.instant());
        } catch (MetaGraphException ex) {
            log.warn("Event {} of business {} not posted on {}: {}", eventId, businessId, platform, ex.getMessage());
            return EventSocialPost.failed(eventId, businessId, platform, explain(platform, ex.getMessage()),
                    clock.instant());
        }
    }

    /** Meta's permission errors, turned into what the owner can do about them. */
    private static String explain(Platform platform, String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("permission") || lower.contains("(#200)") || lower.contains("(#10)")) {
            String scope = platform == Platform.FACEBOOK ? "pages_manage_posts" : "instagram_content_publish";
            return message + " — the platform's Facebook app is missing the " + scope + " permission. Once it is"
                    + " added to the Facebook Login configuration, click Reconnect on Integrations.";
        }
        return message;
    }

    private static String facebookUnavailable(Context context) {
        return context.connection() == null
                ? "Connect your Facebook Page on Integrations to share events." : null;
    }

    private String instagramUnavailable(Context context) {
        MetaConnection connection = context.connection();
        if (connection == null) {
            return "Connect your Facebook Page on Integrations to share events.";
        }
        if (connection.getInstagramAccountId() == null) {
            return "No Instagram account is linked to your Facebook Page.";
        }
        if (context.event().getImageMediaId() == null) {
            return "Instagram posts need a picture — add one to the event.";
        }
        if (!MediaService.convertibleToJpeg(context.imageType())) {
            return "Instagram needs a JPEG or PNG picture; this one is WebP.";
        }
        if (settings.publicBaseUrl().isEmpty()) {
            return "Pictures can only be posted once the platform has a public https address (META_PUBLIC_BASE_URL).";
        }
        return null;
    }

    /** The picture as Meta's servers fetch it, when there is one they can. */
    private Optional<String> pictureUrl(Context context) {
        if (context.event().getImageMediaId() == null || !MediaService.convertibleToJpeg(context.imageType())) {
            return Optional.empty();
        }
        return settings.publicBaseUrl().map(base -> base + "/api/public/media/" + context.event().getImageMediaId()
                + ".jpg");
    }

    /** A starting point: what, when, the details, and where to book. */
    private String caption(Context context) {
        BusinessEvent event = context.event();
        StringBuilder text = new StringBuilder(event.getTitle());
        if (event.getPerformer() != null) {
            text.append(" — ").append(event.getPerformer());
        }
        text.append("\n📅 ").append(DAY.format(event.getDate())).append(" · ").append(TIME.format(event.getStartTime()));
        if (event.getEndTime() != null) {
            text.append("–").append(TIME.format(event.getEndTime()));
        }
        if (event.getDescription() != null) {
            String description = event.getDescription();
            text.append("\n\n").append(description.length() > DESCRIPTION_IN_CAPTION
                    ? description.substring(0, DESCRIPTION_IN_CAPTION) + "…" : description);
        }
        String site = settings.frontendUrl();
        // A localhost link is no use to anyone reading the post.
        if (!site.contains("localhost") && !site.contains("127.0.0.1")) {
            text.append("\n\n").append(event.getServiceId() != null ? "Reserve your spot: " : "More: ")
                    .append(site).append("/b/").append(context.business().getSlug());
        } else if (event.getServiceId() != null) {
            text.append("\n\nMessage us to reserve your spot!");
        }
        return text.toString();
    }
}
