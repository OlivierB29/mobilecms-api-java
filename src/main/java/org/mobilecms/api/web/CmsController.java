package org.mobilecms.api.web;

import java.util.ArrayList;
import java.util.List;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.service.CalendarExportService;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.util.FileTree;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping(ApiConstants.API)
public class CmsController {

    private final ContentService contentService;
    private final FileService fileService;
    private final AppProperties properties;
    private final CalendarExportService calendarExportService;
    private final JsonFiles jsonFiles;

    public CmsController(
            ContentService contentService,
            FileService fileService,
            AppProperties properties,
            CalendarExportService calendarExportService,
            JsonFiles jsonFiles) {
        this.contentService = contentService;
        this.fileService = fileService;
        this.properties = properties;
        this.calendarExportService = calendarExportService;
        this.jsonFiles = jsonFiles;
    }

    @GetMapping("/agenda/events.ics")
    public ResponseEntity<String> events() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header("Content-Disposition", "inline; filename=\"events.ics\"")
                .body(calendarExportService.export());
    }

    @GetMapping("/cmsapi/status")
    public ResponseEntity<Object> status() {
        return ResponseEntity.ok(jsonFiles.object());
    }

    @GetMapping("/cmsapi/content")
    public ResponseEntity<Object> types() {
        return ResponseEntity.ok(contentService.options(properties.getPublicDir(), "types.json"));
    }

    @GetMapping("/cmsapi/content/{type}")
    public ResponseEntity<Object> list(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return AuthController.toResponse(contentService.getAllObjects(properties.getPublicDir(), type));
    }

    @PostMapping("/cmsapi/content/{type}")
    public ResponseEntity<Object> save(@PathVariable String type, @RequestBody ObjectNode body) {
        var putResponse = contentService.post(properties.getPublicDir(), type, ApiConstants.ID, body);
        if (!putResponse.isOk()) {
            return AuthController.toResponse(putResponse);
        }
        ObjectNode saved = (ObjectNode) putResponse.getResult();
        var publish = contentService.publishById(properties.getPublicDir(), type, ApiConstants.ID, saved.get("id").asText());
        if (publish.isOk()) {
            return ResponseEntity.ok(saved);
        }
        return AuthController.toResponse(publish);
    }

    @GetMapping("/cmsapi/content/{type}/{id}")
    public ResponseEntity<Object> get(@PathVariable String type, @PathVariable String id,
            @RequestParam(required = false) Long timestamp) {
        return AuthController.toResponse(contentService.getRecord(properties.getPublicDir(), type, id));
    }

    @DeleteMapping("/cmsapi/content/{type}/{id}")
    public ResponseEntity<Object> delete(@PathVariable String type, @PathVariable String id) {
        var mediaDir = fileService.getRecordDirectory(properties.getMediaDir(), type, id);
        FileTree.deleteDir(mediaDir);
        var response = contentService.deleteRecord(properties.getPublicDir(), type, id);
        if (response.isOk()) {
            response = contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID);
        }
        return AuthController.toResponse(response);
    }

    @PostMapping("/cmsapi/deletelist/{type}")
    public ResponseEntity<Object> deleteList(@PathVariable String type, @RequestBody JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body.isArray()) {
            body.forEach(node -> ids.add(node.asText()));
        }
        contentService.deleteRecords(properties.getPublicDir(), type, ids);
        return AuthController.toResponse(contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID));
    }

    @GetMapping("/cmsapi/index/{type}")
    public ResponseEntity<Object> index(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return AuthController.toResponse(contentService.getAll(properties.getPublicDir(), type + "/index/index.json"));
    }

    @PostMapping("/cmsapi/index/{type}")
    public ResponseEntity<Object> rebuild(@PathVariable String type) {
        return AuthController.toResponse(contentService.rebuildIndex(properties.getPublicDir(), type, ApiConstants.ID));
    }

    @GetMapping("/cmsapi/metadata/{type}")
    public ResponseEntity<Object> metadata(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getMetadataFileName(properties.getPublicDir(), type)));
    }

    @GetMapping("/cmsapi/template/{type}")
    public ResponseEntity<Object> template(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getTemplateFileName(properties.getPublicDir(), type)));
    }

    @GetMapping("/webapi/content/{type}")
    public ResponseEntity<Object> webList(@PathVariable String type, @RequestParam(required = false) Long timestamp) {
        return AuthController.toResponse(contentService.getAllObjectsFromIndexByType(properties.getPublicDir(), type));
    }

    @GetMapping("/webapi/content/{type}/{id}")
    public ResponseEntity<Object> webGet(@PathVariable String type, @PathVariable String id,
            @RequestParam(required = false) Long timestamp) {
        return AuthController.toResponse(contentService.getRecord(properties.getPublicDir(), type, id));
    }
}
