package com.bookingsaas.site;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.GalleryService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.employee.EmployeeService;
import com.bookingsaas.media.Media;
import com.bookingsaas.media.MediaService;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.site.dto.SiteDtos.Photo;
import com.bookingsaas.site.dto.SiteDtos.PublicSiteResponse;
import com.bookingsaas.site.dto.SiteDtos.SaveDraftRequest;
import com.bookingsaas.site.dto.SiteDtos.SiteResponse;
import com.bookingsaas.site.dto.SiteDtos.TeamMember;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The homepage builder: the owner edits a draft, publishing makes it what
 * customers see, and customers only ever read the published copy.
 *
 * <p>Every document that is stored has passed field validation,
 * {@link SiteValidator} and {@link SiteReferences}: it parses, its links are
 * safe, and everything it points at is the business's own.
 */
@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private final SiteConfigRepository repository;
    private final SiteSchema schema;
    private final BusinessService businessService;
    private final GalleryService galleryService;
    private final MediaService mediaService;
    private final ServiceCatalogService serviceCatalog;
    private final EmployeeService employeeService;
    private final ResourceService resourceService;
    private final CurrentTenant currentTenant;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public SiteService(SiteConfigRepository repository, SiteSchema schema, BusinessService businessService,
                       GalleryService galleryService, MediaService mediaService,
                       ServiceCatalogService serviceCatalog, EmployeeService employeeService,
                       ResourceService resourceService, CurrentTenant currentTenant, ObjectMapper objectMapper,
                       Validator validator, Clock clock) {
        this.repository = repository;
        this.schema = schema;
        this.businessService = businessService;
        this.galleryService = galleryService;
        this.mediaService = mediaService;
        this.serviceCatalog = serviceCatalog;
        this.employeeService = employeeService;
        this.resourceService = resourceService;
        this.currentTenant = currentTenant;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    // --- owner -------------------------------------------------------------

    /** The draft and the published page. Opening the builder for the first time stores the default page. */
    @Transactional
    public SiteResponse current() {
        currentTenant.requireOwner();
        return response(loadOrCreate(currentTenant.businessId()));
    }

    @Transactional
    public SiteResponse saveDraft(SaveDraftRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        SiteConfig config = loadOrCreate(businessId);
        if (!request.draftVersion().equals(config.getDraftVersion())) {
            throw ApiException.of(ErrorCode.SITE_DRAFT_OUTDATED,
                    "The page was changed in another window. Reload to see the latest version.");
        }
        config.setDraft(write(clean(businessId, request.document())));
        return response(repository.saveAndFlush(config));
    }

    /** Customers see the draft from now on; the brand colour of emails follows. */
    @Transactional
    public SiteResponse publish() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        SiteConfig config = loadOrCreate(businessId);
        // Checked again: a service or photo may have been deleted since the draft was saved.
        SiteDocument document = clean(businessId, read(config.getDraft()));
        String json = write(document);
        config.setDraft(json);
        config.setPublished(json);
        config.setPublishedAt(clock.instant());
        SiteDocument.Colors colors = document.theme().colors();
        businessService.applyBranding(businessId, colors.primary(), colors.dark());
        log.info("Homepage published for business id={}", businessId);
        return response(repository.saveAndFlush(config));
    }

    /** Throws the draft away: it becomes the published page again. */
    @Transactional
    public SiteResponse discard() {
        currentTenant.requireOwner();
        SiteConfig config = loadOrCreate(currentTenant.businessId());
        config.setDraft(config.getPublished());
        return response(repository.saveAndFlush(config));
    }

    // --- customers ---------------------------------------------------------

    @Transactional(readOnly = true)
    public PublicSiteResponse publicSite(Business business) {
        SiteDocument site = published(business);
        return new PublicSiteResponse(site, team(business.getId(), site), gallery(business.getId(), site));
    }

    /** The theme the booking flow and reservation page are drawn in. */
    @Transactional(readOnly = true)
    public SiteDocument.Theme publishedTheme(Business business) {
        return published(business).theme();
    }

    // --- internals ---------------------------------------------------------

    private SiteDocument published(Business business) {
        return repository.findById(business.getId())
                .map(config -> read(config.getPublished()))
                .orElseGet(() -> defaults(business));
    }

    private SiteConfig loadOrCreate(Long businessId) {
        return repository.findById(businessId).orElseGet(() -> {
            Business business = businessService.requireById(businessId);
            return repository.saveAndFlush(new SiteConfig(businessId, write(defaults(business))));
        });
    }

    private SiteDocument defaults(Business business) {
        return SiteDefaults.forBusiness(business, !galleryService.photosOf(business.getId()).isEmpty());
    }

    /** Validated, with links checked and pointers to anything not the business's own taken out. */
    private SiteDocument clean(Long businessId, SiteDocument document) {
        Set<ConstraintViolation<SiteDocument>> violations = validator.validate(document);
        if (!violations.isEmpty()) {
            ConstraintViolation<SiteDocument> first = violations.iterator().next();
            throw ApiException.validation(first.getPropertyPath() + " " + first.getMessage());
        }
        SiteValidator.check(document, schema.currentVersion());

        Set<Long> serviceIds = serviceCatalog.byIds(businessId, List.copyOf(SiteReferences.serviceIds(document)))
                .stream().map(BookableService::getId).collect(Collectors.toSet());
        Set<Long> employeeIds = employeeService.byIds(businessId, SiteReferences.employeeIds(document))
                .stream().map(Employee::getId).collect(Collectors.toSet());
        SiteReferences.Owned owned = new SiteReferences.Owned(
                mediaService.ownedAmong(businessId, SiteReferences.mediaIds(document)), serviceIds, employeeIds);
        return SiteReferences.retainOwned(document, owned);
    }

    /** The active employees the page's team sections picked, with their photo. */
    private List<TeamMember> team(Long businessId, SiteDocument site) {
        Set<Long> ids = SiteReferences.employeeIds(site);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Employee> employees = employeeService.byIds(businessId, ids).stream()
                .filter(Employee::isActive)
                .toList();
        Map<Long, Resource> resources = resourceService.byIds(businessId,
                        employees.stream().map(Employee::getResourceId).toList()).stream()
                .collect(Collectors.toMap(Resource::getId, Function.identity()));
        List<TeamMember> team = new ArrayList<>();
        for (Employee employee : employees) {
            Resource resource = resources.get(employee.getResourceId());
            team.add(new TeamMember(employee.getId(), employee.getName(),
                    resource == null ? null : Media.urlOf(resource.getImageMediaId())));
        }
        return team;
    }

    private List<Photo> gallery(Long businessId, SiteDocument site) {
        boolean shown = site.sections().stream().anyMatch(section -> section instanceof Section.Gallery gallery
                && gallery.content().source() == Section.GallerySource.GALLERY);
        return shown
                ? galleryService.photosOf(businessId).stream().map(image -> new Photo(image.url(), image.caption()))
                .toList()
                : List.of();
    }

    private SiteResponse response(SiteConfig config) {
        SiteDocument draft = read(config.getDraft());
        SiteDocument published = read(config.getPublished());
        return new SiteResponse(draft, published, config.getDraftVersion(), config.getPublishedAt(),
                !draft.equals(published));
    }

    private SiteDocument read(String json) {
        try {
            return objectMapper.treeToValue(schema.upgrade(objectMapper.readTree(json)), SiteDocument.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A stored site document could not be read", ex);
        }
    }

    private String write(SiteDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A site document could not be written", ex);
        }
    }
}
