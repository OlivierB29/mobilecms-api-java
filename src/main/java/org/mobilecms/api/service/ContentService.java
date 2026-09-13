package org.mobilecms.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.api.util.PathSafety;
import org.mobilecms.api.util.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ContentService {

    private final JsonFiles jsonFiles;
    private final AppProperties properties;
    private final FileService fileService;

    public ContentService(
            JsonFiles jsonFiles,
            AppProperties properties,
            @org.springframework.context.annotation.Lazy FileService fileService) {
        this.jsonFiles = jsonFiles;
        this.properties = properties;
        this.fileService = fileService;
    }

    public ServiceResult getRecord(Path databaseDir, String type, String id) {
        Path file = itemFileWithoutRecord(databaseDir, type, id);
        if (Files.exists(file)) {
            return ServiceResult.ok(jsonFiles.read(file));
        }
        return ServiceResult.error(404, "Record not found " + type + "/" + id);
    }

    public ServiceResult deleteRecord(Path databaseDir, String type, String id) {
        Path file = itemFileWithoutRecord(databaseDir, type, id);
        if (Files.exists(file)) {
            try {
                Files.delete(file);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return ServiceResult.ok(jsonFiles.object());
        }
        return ServiceResult.error(404, "records to delete not found " + type + " : " + id);
    }

    public ServiceResult deleteRecords(Path databaseDir, String type, List<String> ids) {
        ServiceResult last = ServiceResult.ok(jsonFiles.object());
        for (String id : ids) {
            last = deleteRecord(databaseDir, type, id);
        }
        return last;
    }

    public ServiceResult getAllObjects(Path databaseDir, String type) {
        PathSafety.requireType(type);
        ArrayNode list = jsonFiles.array();
        Path dir = databaseDir.resolve(type);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .forEach(path -> {
                            ObjectNode item = jsonFiles.object();
                            String filename = path.getFileName().toString();
                            item.put("filename", filename);
                            item.put("id", filename.replace(".json", ""));
                            list.add(item);
                        });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return ServiceResult.ok(list);
    }

    public ServiceResult getAll(Path databaseDir, String filename) {
        Path file = databaseDir.resolve(filename);
        if (!Files.exists(file)) {
            return new ServiceResult(400, jsonFiles.object());
        }
        return ServiceResult.ok(jsonFiles.read(file));
    }

    public ServiceResult getAllObjectsFromIndexByType(Path databaseDir, String type) {
        Path file = getIndexFileName(databaseDir, type);
        if (!Files.exists(file)) {
            return new ServiceResult(400, jsonFiles.object());
        }
        return ServiceResult.ok(jsonFiles.read(file));
    }

    public JsonNode options(Path databaseDir, String filename) {
        return jsonFiles.read(databaseDir.resolve(filename));
    }

    public ServiceResult post(Path databaseDir, String type, String keyname, ObjectNode record) {
        if (record == null || record.isEmpty()) {
            return ServiceResult.error(400, "Empty object record");
        }
        JsonNode metadata = loadMetadata(databaseDir, type);
        assignTimestampFields(metadata, record);
        boolean isNew = !record.has(keyname) || JsonFiles.isBlank(record.get(keyname));
        if (isNew) {
            IdGenerator.assignGeneratedId(keyname, record, metadata);
        }
        if (!record.has(keyname) || JsonFiles.isBlank(record.get(keyname))) {
            return ServiceResult.error(400, "Bad object parameters");
        }
        String id = record.get(keyname).asText();
        Path file = getItemFileName(databaseDir, type, id, record);
        if (!isNew) {
            refreshThumbnails(type, id, record);
        }
        jsonFiles.write(file, record);
        return ServiceResult.ok(record);
    }

    public ServiceResult update(Path databaseDir, String type, String keyname, ObjectNode record) {
        if (record == null || record.isEmpty()) {
            return ServiceResult.error(400, "Bad object parameters");
        }
        assignTimestampFields(loadMetadata(databaseDir, type), record);
        String id = record.get(keyname).asText();
        Path file = getItemFileName(databaseDir, type, id, record);
        ObjectNode existing = (ObjectNode) jsonFiles.read(file);
        jsonFiles.copy(record, existing);
        jsonFiles.write(file, existing);
        return ServiceResult.ok(existing);
    }

    public ServiceResult publishById(Path databaseDir, String type, String keyname, String keyvalue) {
        Path indexFile = getIndexFileName(databaseDir, type);
        ObjectNode indexValue;
        Path cacheTemplate = getCacheTemplateFileName(databaseDir, type);
        if (Files.exists(cacheTemplate)) {
            indexValue = (ObjectNode) jsonFiles.read(cacheTemplate).deepCopy();
        } else {
            indexValue = (ObjectNode) jsonFiles.read(getIndexTemplateFileName(databaseDir, type)).deepCopy();
        }
        Path recordFile = databaseDir.resolve(type).resolve(keyvalue + ".json");
        JsonNode record = jsonFiles.read(recordFile);
        jsonFiles.copy(record, indexValue);
        ArrayNode data = Files.exists(indexFile) ? (ArrayNode) jsonFiles.read(indexFile) : jsonFiles.array();
        data = jsonFiles.put(data, keyname, indexValue);
        sortIndex(data, databaseDir, type, keyname);
        jsonFiles.write(indexFile, data);
        return ServiceResult.ok(jsonFiles.object());
    }

    public ServiceResult rebuildIndex(Path databaseDir, String type, String keyname) {
        ArrayNode data = jsonFiles.array();
        Path indexFile = getIndexFileName(databaseDir, type);
        JsonNode indexTemplate = jsonFiles.read(getIndexTemplateFileName(databaseDir, type));
        Path typeDir = databaseDir.resolve(type);
        if (Files.isDirectory(typeDir)) {
            try (Stream<Path> stream = Files.list(typeDir)) {
                stream.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .forEach(path -> {
                            JsonNode record = jsonFiles.read(path);
                            ObjectNode indexValue = (ObjectNode) indexTemplate.deepCopy();
                            jsonFiles.copy(record, indexValue);
                            data.add(indexValue);
                        });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        RecordConf conf = loadRecordConf(databaseDir, type);
        sortIndex(data, databaseDir, type, keyname);
        Path cacheTemplate = getCacheTemplateFileName(databaseDir, type);
        if (Files.exists(cacheTemplate)) {
            JsonNode cache = jsonFiles.read(cacheTemplate);
            int cacheSize = conf.cacheSize;
            int i = 0;
            while (i < cacheSize && i < data.size()) {
                String file = data.get(i).get(keyname).asText();
                JsonNode record = jsonFiles.read(databaseDir.resolve(type).resolve(file + ".json"));
                ObjectNode cacheValue = (ObjectNode) cache.deepCopy();
                jsonFiles.copy(record, cacheValue);
                data.set(i, cacheValue);
                i++;
            }
        }
        jsonFiles.write(indexFile, data);
        return ServiceResult.ok(jsonFiles.object());
    }

    public Path getMetadataFileName(Path databaseDir, String type) {
        PathSafety.requireType(type);
        return databaseDir.resolve(type).resolve("index").resolve("metadata.json");
    }

    public Path getTemplateFileName(Path databaseDir, String type) {
        PathSafety.requireType(type);
        return databaseDir.resolve(type).resolve("index").resolve("new.json");
    }

    public Path getIndexFileName(Path databaseDir, String type) {
        PathSafety.requireType(type);
        return databaseDir.resolve(type).resolve("index").resolve("index.json");
    }

    public Path getIndexTemplateFileName(Path databaseDir, String type) {
        PathSafety.requireType(type);
        return databaseDir.resolve(type).resolve("index").resolve("index_template.json");
    }

    public Path getCacheTemplateFileName(Path databaseDir, String type) {
        PathSafety.requireType(type);
        return databaseDir.resolve(type).resolve("index").resolve("cache_template.json");
    }

    public Path getItemFileName(Path databaseDir, String type, String id, JsonNode record) {
        Path result = databaseDir.resolve(type);
        RecordConf conf = loadRecordConf(databaseDir, type);
        if (conf.organizeBy != null && !conf.organizeBy.isEmpty() && record != null && record.has(conf.organizeField)) {
            String recordDate = JsonFiles.text(record.get(conf.organizeField));
            if (recordDate != null && recordDate.length() >= 4) {
                result = result.resolve(recordDate.substring(0, 4));
            }
        }
        return result.resolve(id + ".json");
    }

    public Path itemFileWithoutRecord(Path databaseDir, String type, String id) {
        PathSafety.requireType(type);
        PathSafety.requireId(id);
        return databaseDir.resolve(type).resolve(id + ".json");
    }

    private void sortIndex(ArrayNode data, Path databaseDir, String type, String keyname) {
        RecordConf conf = loadRecordConf(databaseDir, type);
        String sortBy = conf.sortBy == null || conf.sortBy.isEmpty() ? keyname : conf.sortBy;
        boolean ascending = "asc".equals(conf.sortDirection);
        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        Comparator<JsonNode> comparator = (a, b) -> {
            if (a == null || b == null || JsonFiles.isBlank(a.get(sortBy)) || JsonFiles.isBlank(b.get(sortBy))) {
                return 0;
            }
            int cmp = StringUtils.strnatcmp(a.get(sortBy).asText(), b.get(sortBy).asText());
            return ascending ? cmp : -cmp;
        };
        items.sort(comparator);
        data.removeAll();
        items.forEach(data::add);
    }

    private JsonNode loadMetadata(Path databaseDir, String type) {
        Path file = getMetadataFileName(databaseDir, type);
        if (!Files.exists(file)) {
            return jsonFiles.array();
        }
        JsonNode metadata = jsonFiles.read(file);
        return metadata.isArray() ? metadata : jsonFiles.array();
    }

    private void assignTimestampFields(JsonNode metadata, ObjectNode record) {
        if (metadata == null || !metadata.isArray()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        for (JsonNode field : metadata) {
            if ("timestamp".equals(JsonFiles.text(field.get("type"))) && field.has("name")) {
                record.put(field.get("name").asText(), now);
            }
        }
    }

    private void refreshThumbnails(String type, String id, ObjectNode record) {
        if (!record.has("media") || !record.get("media").isArray()) {
            return;
        }
        List<JsonNode> media = new ArrayList<>();
        record.get("media").forEach(media::add);
        fileService.createThumbnails(
                properties.getMediaDir(),
                type,
                id,
                media,
                properties.getIntegerArray("thumbnailsizes"),
                properties.getInteger("imagequality", 100),
                List.of(100, 200),
                80);
    }

    public RecordConf loadRecordConf(Path databaseDir, String type) {
        Path file = databaseDir.resolve(type).resolve("index").resolve("conf.json");
        RecordConf conf = new RecordConf();
        if (!Files.exists(file)) {
            return conf;
        }
        JsonNode node = jsonFiles.read(file);
        conf.organizeBy = JsonFiles.text(node.get("organizeby"));
        conf.organizeField = JsonFiles.text(node.get("organizefield"));
        conf.sortBy = JsonFiles.text(node.get("sortby"));
        conf.sortDirection = JsonFiles.text(node.get("sortdirection"));
        if (node.has("cachesize") && node.get("cachesize").isNumber()) {
            conf.cacheSize = node.get("cachesize").intValue();
        }
        if (node.has("typethumbnailsizes") && node.get("typethumbnailsizes").isArray()) {
            node.get("typethumbnailsizes").forEach(item -> conf.thumbnailSizes.add(item.intValue()));
        }
        return conf;
    }

    public static class RecordConf {
        String organizeBy = "";
        String organizeField = "";
        String sortBy = "";
        String sortDirection = "";
        int cacheSize;
        List<Integer> thumbnailSizes = new ArrayList<>();
    }
}
