package com.pantrybase.api.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final RecommendationService recommendationService;

    public AiController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/recommend")
    public ResponseEntity<List<RecommendationService.RecommendationResponse>> recommend(@AuthenticationPrincipal Long userId) {
        List<RecommendationService.RecommendationResponse> recommendations = recommendationService.recommendForUser(userId);
        return ResponseEntity.ok(recommendations);
    }
}