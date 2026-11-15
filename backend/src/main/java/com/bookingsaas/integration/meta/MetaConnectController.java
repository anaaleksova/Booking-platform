package com.bookingsaas.integration.meta;

import com.bookingsaas.integration.meta.MetaConnectDtos.ChoosePageRequest;
import com.bookingsaas.integration.meta.MetaConnectDtos.CompleteRequest;
import com.bookingsaas.integration.meta.MetaConnectDtos.CompleteResponse;
import com.bookingsaas.integration.meta.MetaConnectDtos.MetaView;
import com.bookingsaas.integration.meta.MetaConnectDtos.StartResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integrations/meta")
@Tag(name = "Admin · Integrations")
public class MetaConnectController {

    private final MetaConnectService service;

    public MetaConnectController(MetaConnectService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public StartResponse start() {
        return service.start();
    }

    @PostMapping("/complete")
    public CompleteResponse complete(@Valid @RequestBody CompleteRequest request) {
        return service.complete(request.code(), request.state());
    }

    @PostMapping("/choose-page")
    public MetaView choosePage(@Valid @RequestBody ChoosePageRequest request) {
        return service.choosePage(request.sessionId(), request.pageId());
    }

    @DeleteMapping
    public MetaView disconnect() {
        return service.disconnect();
    }
}
