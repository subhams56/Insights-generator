package com.insights.generator.controller;

import com.insights.generator.agent.NLQAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class QueryController {

    private final NLQAgent nlqAgent;

    public QueryController(NLQAgent nlqAgent) {
        this.nlqAgent = nlqAgent;
    }

    @GetMapping("/ask")
    public ResponseEntity<Object> askQuestion(@RequestParam String query) {
        Object response = nlqAgent.processQuestion(query);
        return ResponseEntity.ok(response);
    }
}