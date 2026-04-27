package com.example.nl2sql.controller;

import com.example.nl2sql.model.DataQueryResponse;
import com.example.nl2sql.service.DataQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DataQueryController {

    private final DataQueryService dataQueryService;

    public DataQueryController(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @GetMapping("/query")
    public DataQueryResponse query(@RequestParam String question) {
        return dataQueryService.query(question);
    }
}
