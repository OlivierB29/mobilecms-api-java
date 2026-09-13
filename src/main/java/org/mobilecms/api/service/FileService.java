package org.mobilecms.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.api.util.PathSafety;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class FileService {

    private final AppProperties properties;
    private final JsonFiles jsonFiles;
    private final ImageService imageService;
    private final ContentService contentService;

    public FileService(
            AppProperties properties,
            JsonFiles jsonFiles,
            ImageService imageService,
            @org.springframework.context.annotation.Lazy ContentService contentService) {
        this.properties = properties;
        this.jsonFiles = jsonFiles;
        this.imageService = imageService;
        this.contentService = contentService;
    }

    public Path getRecordDirectory(Path mediaDir, String type, String id) {
        if (mediaDir == null || type == null || type.isEmpty() || id == null || id.isEmpty()) {
            throw new IllegalArgumentException("getMediaDirectory() mediadir " + mediaDir + " type " + type + " id " + id);
        }
        return mediaDir.resolve(type).resolve(id);
    }

    public List<ObjectNode> getDescriptions(Path dir) {
        List<ObjectNode> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> result.add(getFileResponse(path, path.getFileName().toString())));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    public ObjectNode getFileResponse(Path destfile, String title) {
        ObjectNode result;
        if (imageService.isImage(destfile)) {
            result = imageService.imageInfo(destfile);
        } else {
            result = jsonFiles.object();
            result.put("mimetype", imageService.mimeType(destfile));
        }
        try {
            result.put("size", Files.size(destfile));
        } catch (IOException e) {
            result.put("size", 0);
        }
        result.put("title", title);
        result.put("url", destfile.getFileName().toString());
        return result;
    }

    public List<ObjectNode> upload(String type, String id, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw org.mobilecms.api.error.ApiException.badRequest("empty files array.");
        }
        long max = properties.getUploadMaxFileSize();
        List<String> extensions = properties.getStringArray("fileextensions");
        List<String> mimeTypes = properties.getStringArray("mimetypes");
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (max > 0 && file.getSize() > max) {
                throw org.mobilecms.api.error.ApiException.payloadTooLarge("Uploaded file exceeds the maximum allowed size.");
            }
            String extension = PathSafety.extension(file.getOriginalFilename());
            String contentType = file.getContentType();
            if (!extensions.contains(extension.toLowerCase(Locale.ROOT))
                    || (contentType != null && !mimeTypes.isEmpty() && !mimeTypes.contains(contentType))) {
                throw org.mobilecms.api.error.ApiException.unsupportedMediaType("Unsupported file type.");
            }
        }
        Path destDir = getRecordDirectory(properties.getMediaDir(), type, id);
        try {
            Files.createDirectories(destDir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<ObjectNode> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.getOriginalFilename() == null) {
                continue;
            }
            String filename = Path.of(file.getOriginalFilename()).getFileName().toString();
            Path dest = destDir.resolve(filename);
            try {
                file.transferTo(dest);
                result.add(getFileResponse(dest, filename));
            } catch (IOException e) {
                throw new IllegalStateException("Upload error " + filename, e);
            }
        }
        return result;
    }

    public ServiceResult deleteMediaFiles(String type, String id, ArrayNode files) {
        Path destDir = getRecordDirectory(properties.getMediaDir(), type, id);
        ServiceResult record = contentService.getRecord(properties.getPublicDir(), type, id);
        for (JsonNode file : files) {
            if (record.getResult() instanceof ObjectNode object
                    && object.has("media")
                    && object.get("media").isArray()) {
                for (JsonNode fileInRecord : object.get("media")) {
                    if (fileInRecord.has("url") && file.has("url")
                            && fileInRecord.get("url").asText().equals(file.get("url").asText())) {
                        deleteThumbnailFiles(fileInRecord, type, id);
                    }
                }
            }
            if (!file.has("url")) {
                throw new IllegalArgumentException("wrong file KO");
            }
            Path destfile = destDir.resolve(Path.of(file.get("url").asText()).getFileName().toString());
            try {
                Files.deleteIfExists(destfile);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return ServiceResult.ok(getDescriptions(destDir));
    }

    private void deleteThumbnailFiles(JsonNode fileInRecord, String type, String id) {
        if (!fileInRecord.has("thumbnails") || fileInRecord.get("thumbnails").isNull()) {
            return;
        }
        for (JsonNode thumbnail : fileInRecord.get("thumbnails")) {
            Path thumbnailPath = properties.getMediaDir()
                    .resolve(type)
                    .resolve(id)
                    .resolve("thumbnails")
                    .resolve(thumbnail.get("url").asText());
            try {
                Files.deleteIfExists(thumbnailPath);
            } catch (IOException e) {
                throw new IllegalStateException("delete " + thumbnailPath + " KO", e);
            }
        }
    }

    public ServiceResult createThumbnails(
            Path mediaDir,
            String type,
            String id,
            List<JsonNode> files,
            List<Integer> defaultSizes,
            int quality,
            List<Integer> defaultPdfSizes,
            int pdfQuality) {
        Path destDir = getRecordDirectory(mediaDir, type, id);
        List<ObjectNode> result = new ArrayList<>();
        List<Integer> typeSizes = contentService.loadRecordConf(properties.getPublicDir(), type).thumbnailSizes;
        for (JsonNode file : files) {
            if (!file.has("url")) {
                throw new IllegalArgumentException("wrong file KO");
            }
            Path filePath = destDir.resolve(Path.of(file.get("url").asText()).getFileName().toString());
            Path thumbDir = destDir.resolve("thumbnails");
            if (!Files.exists(filePath)) {
                continue;
            }
            if (imageService.isImage(filePath)) {
                List<Integer> sizes = typeSizes.isEmpty() ? defaultSizes : typeSizes;
                List<ObjectNode> thumbs = imageService.multipleResize(filePath, thumbDir, sizes, quality);
                ObjectNode fileResponse = imageService.imageInfo(filePath);
                fileResponse.set("thumbnails", toArray(thumbs));
                result.add(fileResponse);
            } else {
                List<Integer> sizes = typeSizes.isEmpty() ? defaultPdfSizes : typeSizes;
                List<ObjectNode> thumbs = imageService.multiplePdfResize(filePath, thumbDir, sizes, pdfQuality);
                ObjectNode fileResponse = imageService.pdfInfo(filePath);
                if (!thumbs.isEmpty()) {
                    fileResponse.set("thumbnails", toArray(thumbs));
                    result.add(fileResponse);
                }
            }
        }
        return ServiceResult.ok(result);
    }

    public ObjectNode uploadBanner(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw org.mobilecms.api.error.ApiException.badRequest("No banner file uploaded.");
        }
        String filename = Path.of(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).getFileName().toString();
        String extension = PathSafety.extension(filename);
        if (filename.isEmpty() || !List.of("jpg", "jpeg", "png", "gif").contains(extension)) {
            throw org.mobilecms.api.error.ApiException.badRequest("Forbidden banner file type.");
        }
        Path directory = properties.getMediaDir().resolve("theme").resolve("banner");
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create banner directory.", e);
        }
        String imageUrl = "media/theme/banner/" + filename;
        Path themeFile = properties.getPublicDir().resolve("theme").resolve("theme.json");
        ObjectNode theme = Files.exists(themeFile) ? (ObjectNode) jsonFiles.read(themeFile) : jsonFiles.object();
        ObjectNode banner = theme.has("banner") && theme.get("banner").isObject()
                ? (ObjectNode) theme.get("banner")
                : jsonFiles.object();
        banner.put("imageurl", imageUrl);
        theme.set("banner", banner);
        jsonFiles.write(themeFile, theme);
        ObjectNode result = jsonFiles.object();
        result.put("url", imageUrl);
        result.put("title", filename);
        return result;
    }

    private ArrayNode toArray(List<ObjectNode> thumbs) {
        ArrayNode array = jsonFiles.array();
        thumbs.forEach(array::add);
        return array;
    }

}
