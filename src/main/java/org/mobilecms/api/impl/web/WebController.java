package org.mobilecms.api.impl.web;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.generated.web.api.WebApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebController implements WebApi {

    private final ContentService contentService;
    private final AppProperties properties;

    public WebController(ContentService contentService, AppProperties properties) {
        this.contentService = contentService;
        this.properties = properties;
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/webapi/content")
    public ResponseEntity<Object> publicTypes() {
        return ResponseEntity.ok(contentService.options(properties.getPublicDir(), "types.json"));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/webapi/content/{type}")
    public ResponseEntity<Object> webList(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return toResponse(contentService.getAllObjectsFromIndexByType(properties.getPublicDir(), type));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/webapi/content/{type}/{id}")
    public ResponseEntity<Object> webGet(@PathVariable String type, @PathVariable String id,
            @RequestParam(required = false) Long timestamp) {
        return toResponse(contentService.getRecord(properties.getPublicDir(), type, id));
    }

    private static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }
}
