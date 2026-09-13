package org.mobilecms.api.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class CalendarExportService {

    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ContentService contentService;
    private final AppProperties properties;

    public CalendarExportService(ContentService contentService, AppProperties properties) {
        this.contentService = contentService;
        this.properties = properties;
    }

    public String export() {
        ServiceResult events = contentService.getAllObjects(properties.getPublicDir(), "calendar");
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:" + properties.getString("eventprodid"));
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("X-WR-CALNAME:" + properties.getString("eventxwrcalname"));
        String sitePrefix = properties.getString("eventsiteprefix");
        if (events.getResult() instanceof JsonNode array) {
            for (JsonNode eventReference : array) {
                ServiceResult eventResponse = contentService.getRecord(
                        properties.getPublicDir(),
                        "calendar",
                        eventReference.get("id").asText());
                if (!eventResponse.isOk()) {
                    continue;
                }
                JsonNode event = (JsonNode) eventResponse.getResult();
                String id = event.has("id") ? event.get("id").asText() : eventReference.get("id").asText();
                lines.add("BEGIN:VEVENT");
                lines.add("UID:" + escape(id) + properties.getString("eventcalname"));
                lines.add("URL:" + escape(sitePrefix + id));
                if (event.has("startdate") && !JsonFiles.isBlank(event.get("startdate"))) {
                    lines.add("DTSTART:" + formatDateTime(event.path("date").asText(), event.get("startdate").asText()));
                    if (event.has("enddate") && !JsonFiles.isBlank(event.get("enddate"))) {
                        lines.add("DTEND:" + formatDateTime(event.path("date").asText(), event.get("enddate").asText()));
                    }
                } else {
                    LocalDate startDate = LocalDate.parse(event.get("date").asText());
                    lines.add("DTSTART;VALUE=DATE:" + startDate.format(ICS_DATE));
                    lines.add("DTEND;VALUE=DATE:" + startDate.plusDays(1).format(ICS_DATE));
                }
                lines.add("SUMMARY:" + escape(event.path("title").asText("")));
                String description = event.path("description").asText("").replaceAll("<[^>]+>", "");
                if (!description.isEmpty()) {
                    lines.add("DESCRIPTION:" + escape(sitePrefix + id));
                }
                if (event.has("location") && !JsonFiles.isBlank(event.get("location"))) {
                    lines.add("LOCATION:" + escape(event.get("location").asText()));
                }
                lines.add("END:VEVENT");
            }
        }
        lines.add("END:VCALENDAR");
        return String.join("\r\n", lines) + "\r\n";
    }

    private String formatDateTime(String date, String time) {
        String value = time;
        if (!time.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            value = (date + " " + time).trim();
        }
        LocalDateTime dateTime = LocalDateTime.parse(value.replace(" ", "T"));
        return dateTime.atOffset(ZoneOffset.UTC).format(ICS_UTC);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\r", "\\n")
                .replace("\n", "\\n");
    }
}
