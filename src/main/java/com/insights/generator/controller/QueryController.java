package com.insights.generator.controller;

import com.insights.generator.agent.NLQAgent;
import com.insights.generator.agent.OrchestratorService;
import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import io.swagger.v3.oas.annotations.Operation;
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
    private final OrchestratorService orchestrator;

    public QueryController(NLQAgent nlqAgent, QueryLogRepository logRepository, OrchestratorService orchestrator) {
        this.nlqAgent = nlqAgent;
        this.logRepository = logRepository;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/ask")
    @Operation(summary = "Ask a natural language question about the 5G Telecom dataset.Returns JSON data directly")
    public ResponseEntity<Object> askQuestion(@RequestParam String query) {
        Object response = nlqAgent.processQuestion(query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/askV2")
    @Operation(summary = "Ask a natural language question about the 5G Telecom dataset.Returns a refined, human-friendly answer")
    public ResponseEntity<Object> askQuestionV2(@RequestParam String query) {
        Object result = orchestrator.routeAndExecute(query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    @Operation( summary = "Get the history of all questions asked and their corresponding answers, sorted by most recent first")
    public ResponseEntity<List<QueryLog>> getQueryHistory() {
        // Returns all questions and answers, newest first
        List<QueryLog> history = logRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(history);
    }
}