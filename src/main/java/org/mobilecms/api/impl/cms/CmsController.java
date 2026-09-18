package org.mobilecms.api.impl.cms;

import java.util.ArrayList;
import java.util.List;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.api.util.FileTree;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.generated.cms.api.CmsApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class CmsController implements CmsApi {

    private final ContentService contentService;
    private final FileService fileService;
    private final AppProperties properties;
    private final JsonFiles jsonFiles;

    public CmsController(
            ContentService contentService,
            FileService fileService,
            AppProperties properties,
            JsonFiles jsonFiles) {
        this.contentService = contentService;
        this.fileService = fileService;
        this.properties = properties;
        this.jsonFiles = jsonFiles;
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/content")
    public ResponseEntity<Object> types() {
        return ResponseEntity.ok(contentService.options(properties.getPublicDir(), "types.json"));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/content/{type}")
    public ResponseEntity<Object> list1(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return toResponse(contentService.getAllObjects(properties.getPublicDir(), type));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/cmsapi/content/{type}")
    public ResponseEntity<Object> save(@PathVariable String type, @RequestBody Object body) {
        JsonNode payload = asJsonNode(body);
        if (!(payload instanceof ObjectNode objectNode)) {
            return toResponse(ServiceResult.error(400, "content payload must be a JSON object"));
        }
        var putResponse = contentService.post(properties.getPublicDir(), type, ApiConstants.ID, objectNode);
        if (!putResponse.isOk()) {
            return toResponse(putResponse);
        }
        ObjectNode saved = (ObjectNode) putResponse.getResult();
        var publish = contentService.publishById(properties.getPublicDir(), type, ApiConstants.ID, saved.get("id").asText());
        if (publish.isOk()) {
            return ResponseEntity.ok(saved);
        }
        return toResponse(publish);
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/content/{type}/{id}")
    public ResponseEntity<Object> get1(@PathVariable String type, @PathVariable String id,
            @RequestParam(required = false) Long timestamp) {
        return toResponse(contentService.getRecord(properties.getPublicDir(), type, id));
    }

    @Override
    @DeleteMapping("/mobilecmsapi/v50/cmsapi/content/{type}/{id}")
    public ResponseEntity<Object> delete2(@PathVariable String type, @PathVariable String id) {
        var mediaDir = fileService.getRecordDirectory(properties.getMediaDir(), type, id);
        org.mobilecms.api.util.FileTree.deleteDir(mediaDir);
        var response = contentService.deleteRecord(properties.getPublicDir(), type, id);
        if (response.isOk()) {
            response = contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID);
        }
        return toResponse(response);
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/cmsapi/deletelist/{type}")
    public ResponseEntity<Object> deleteList(@PathVariable String type, @RequestBody Object body) {
        JsonNode payload = asJsonNode(body);
        List<String> ids = new ArrayList<>();
        if (payload.isArray()) {
            payload.forEach(node -> ids.add(node.asText()));
        }
        contentService.deleteRecords(properties.getPublicDir(), type, ids);
        return toResponse(contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/index/{type}")
    public ResponseEntity<Object> index(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return toResponse(contentService.getAll(properties.getPublicDir(), type + "/index/index.json"));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/cmsapi/index/{type}")
    public ResponseEntity<Object> rebuild(@PathVariable String type) {
        return toResponse(contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/metadata/{type}")
    public ResponseEntity<Object> metadata(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getMetadataFileName(properties.getPublicDir(), type)));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/template/{type}")
    public ResponseEntity<Object> template(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getTemplateFileName(properties.getPublicDir(), type)));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/cmsapi/status")
    public ResponseEntity<Object> status() {
        return ResponseEntity.ok(jsonFiles.object());
    }

    private JsonNode asJsonNode(Object body) {
        if (body == null) {
            return jsonFiles.mapper().createObjectNode();
        }
        if (body instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return jsonFiles.mapper().valueToTree(body);
    }

    private static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }
}
