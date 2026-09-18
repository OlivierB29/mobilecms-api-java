package org.mobilecms.api.impl.misc;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.service.CalendarExportService;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.generated.misc.api.MiscApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MiscController implements MiscApi {

    private final JsonFiles jsonFiles;
    private final CalendarExportService calendarExportService;

    public MiscController(JsonFiles jsonFiles, CalendarExportService calendarExportService) {
        this.jsonFiles = jsonFiles;
        this.calendarExportService = calendarExportService;
    }

    @Override
    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping(ApiConstants.API + "/debugapi")
    public ResponseEntity<Object> debug() {
        var result = jsonFiles.object();
        HttpServletRequest request = currentRequest();
        if (request != null) {
            result.put("uri", request.getRequestURI());
        }
        return ResponseEntity.ok(result);
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/agenda/events.ics")
    public ResponseEntity<String> events() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header("Content-Disposition", "inline; filename=\"events.ics\"")
                .body(calendarExportService.export());
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
