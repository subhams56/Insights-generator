package com.insights.generator.controller;

import com.insights.generator.agent.NLQAgent;
import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/insights")
public class QueryController {

    private final NLQAgent nlqAgent;
    private final QueryLogRepository logRepository;

    public QueryController(NLQAgent nlqAgent, QueryLogRepository logRepository) {
        this.nlqAgent = nlqAgent;
        this.logRepository = logRepository;
    }

    @GetMapping("/ask")
    public ResponseEntity<Object> askQuestion(@RequestParam String query) {
        Object response = nlqAgent.processQuestion(query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/askV2")
    public ResponseEntity<Object> askQuestionV2(@RequestParam String query) {
        return ResponseEntity.ok(nlqAgent.processQuestionV2(query));
    }

    @GetMapping("/history")
    public ResponseEntity<List<QueryLog>> getQueryHistory() {
        // Returns all questions and answers, newest first
        List<QueryLog> history = logRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(history);
    }
}