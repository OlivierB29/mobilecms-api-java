package org.mobilecms.api.impl.file;

import java.util.ArrayList;
import java.util.List;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.error.ApiException;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.generated.file.api.FilesApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@RestController
public class FileController implements FilesApi {

    private final FileService fileService;
    private final AppProperties properties;

    public FileController(FileService fileService, AppProperties properties) {
        this.fileService = fileService;
        this.properties = properties;
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/fileapi/basicupload/{type}/{id}")
    public ResponseEntity<Object> callList(@PathVariable String type, @PathVariable String id) {
        var dest = fileService.getRecordDirectory(properties.getMediaDir(), type, id);
        return ResponseEntity.ok(fileService.getDescriptions(dest));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/fileapi/basicupload/{type}/{id}")
    public ResponseEntity<Object> upload(
            @PathVariable String type,
            @PathVariable String id,
            @RequestPart(value = "uploadfiles", required = false) List<MultipartFile> uploadfiles) {
        if (uploadfiles == null || uploadfiles.isEmpty()) {
            throw ApiException.badRequest("empty files array.");
        }
        return ResponseEntity.ok(fileService.upload(type, id, uploadfiles));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/fileapi/delete/{type}/{id}")
    public ResponseEntity<Object> delete(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody Object body) {
        ArrayNode files = asArrayNode(body);
        return toResponse(fileService.deleteMediaFiles(type, id, files));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/fileapi/thumbnails/{type}/{id}")
    public ResponseEntity<Object> thumbnails(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody Object body) {
        ArrayNode files = asArrayNode(body);
        List<JsonNode> list = new ArrayList<>();
        files.forEach(list::add);
        return toResponse(fileService.createThumbnails(
                properties.getMediaDir(),
                type,
                id,
                list,
                properties.getIntegerArray("thumbnailsizes"),
                properties.getInteger("imagequality", 100),
                List.of(100, 200),
                80));
    }

    private ArrayNode asArrayNode(Object body) {
        if (body == null) {
            return new com.fasterxml.jackson.databind.node.ArrayNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        }
        JsonNode jsonNode = body instanceof JsonNode ? (JsonNode) body : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body);
        if (jsonNode.isArray()) {
            return (ArrayNode) jsonNode;
        }
        throw ApiException.badRequest("expected a JSON array");
    }

    private static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }
}
