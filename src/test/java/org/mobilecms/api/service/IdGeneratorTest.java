package org.mobilecms.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class IdGeneratorTest {

    @Test
    void assignsSlugFromGeneratedSources() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode metadata = mapper.createArrayNode();
        ObjectNode idField = mapper.createObjectNode();
        idField.put("name", "id");
        idField.put("generated", "date,title");
        metadata.add(idField);
        ObjectNode dateField = mapper.createObjectNode();
        dateField.put("name", "date");
        dateField.put("editor", "date");
        metadata.add(dateField);
        ObjectNode titleField = mapper.createObjectNode();
        titleField.put("name", "title");
        metadata.add(titleField);

        ObjectNode record = mapper.createObjectNode();
        record.put("date", "2026-10-17");
        record.put("title", "Stage Iaido");

        String id = IdGenerator.assignGeneratedId("id", record, metadata);
        assertEquals("2026-stage-iaido", id);
        assertEquals(id, record.get("id").asText());
    }
}
