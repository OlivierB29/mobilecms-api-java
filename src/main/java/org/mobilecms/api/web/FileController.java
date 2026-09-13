package org.mobilecms.api.web;

import java.util.Arrays;
import java.util.List;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.error.ApiException;
import org.mobilecms.api.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.node.ArrayNode;

@RestController
@RequestMapping(ApiConstants.API + "/fileapi")
public class FileController {

    private final FileService fileService;
    private final AppProperties properties;

    public FileController(FileService fileService, AppProperties properties) {
        this.fileService = fileService;
        this.properties = properties;
    }

    @GetMapping("/basicupload/{type}/{id}")
    public ResponseEntity<Object> list(@PathVariable String type, @PathVariable String id) {
        var dest = fileService.getRecordDirectory(properties.getMediaDir(), type, id);
        return ResponseEntity.ok(fileService.getDescriptions(dest));
    }

    @PostMapping("/basicupload/{type}/{id}")
    public ResponseEntity<Object> upload(
            @PathVariable String type,
            @PathVariable String id,
            @RequestPart(value = "uploadfiles", required = false) MultipartFile[] uploadfiles) {
        if (uploadfiles == null || uploadfiles.length == 0) {
            throw ApiException.badRequest("empty files array.");
        }
        return ResponseEntity.ok(fileService.upload(type, id, Arrays.asList(uploadfiles)));
    }

    @PostMapping("/delete/{type}/{id}")
    public ResponseEntity<Object> delete(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody ArrayNode files) {
        return AuthController.toResponse(fileService.deleteMediaFiles(type, id, files));
    }

    @PostMapping("/thumbnails/{type}/{id}")
    public ResponseEntity<Object> thumbnails(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody ArrayNode files) {
        List<com.fasterxml.jackson.databind.JsonNode> list = new java.util.ArrayList<>();
        files.forEach(list::add);
        return AuthController.toResponse(fileService.createThumbnails(
                properties.getMediaDir(),
                type,
                id,
                list,
                properties.getIntegerArray("thumbnailsizes"),
                properties.getInteger("imagequality", 100),
                List.of(100, 200),
                80));
    }
}
