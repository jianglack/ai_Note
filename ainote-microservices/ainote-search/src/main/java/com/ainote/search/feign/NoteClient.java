package com.ainote.search.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "note-service")
public interface NoteClient {
    @GetMapping("/api/notes/all")
    Map<String, Object> listAllNotes(@RequestParam("page") int page, @RequestParam("size") int size);
}
