package org.mobilecms.api.web;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MiscController {

    private final JsonFiles jsonFiles;

    public MiscController(JsonFiles jsonFiles) {
        this.jsonFiles = jsonFiles;
    }

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.ok().build();
    }

    @GetMapping(ApiConstants.API + "/debugapi")
    public ResponseEntity<Object> debug(HttpServletRequest request) {
        var result = jsonFiles.object();
        result.put("uri", request.getRequestURI());
        return ResponseEntity.ok(result);
    }
}
