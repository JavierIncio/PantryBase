package com.pantrybase.api.ai;

import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.service.PantryService;
import com.pantrybase.api.recipe.dto.RecipeSearchRequest;
import com.pantrybase.api.recipe.service.RecipeService;
import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.domain.FilterMode;
import com.pantrybase.api.user.domain.ThemeId;
import com.pantrybase.api.user.dto.UserPreferencesResponse;
import com.pantrybase.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private FallbackChatClient fallbackChatClient;
    @Mock
    private PantryService pantryService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private UserService userService;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void disabledAi_returnsEmptyAndNeverTouchesRecipes() {
        when(userService.getPreferences(7L)).thenReturn(preferences(false));

        assertThat(recommendationService.recommendForUser(7L)).isEmpty();

        verify(recipeService, never()).search(anyLong(), any());
        verify(fallbackChatClient, never()).callWithFunction(any(), anyString(), any());
    }

    @Test
    void enabledAi_queriesTheLocalCatalogWithANonNullEmptyFilter() {
        when(userService.getPreferences(7L)).thenReturn(preferences(true));
        when(pantryService.list(7L)).thenReturn(List.<PantryItemResponse>of());
        when(recipeService.search(anyLong(), any())).thenReturn(List.of());
        when(fallbackChatClient.callWithFunction(any(), anyString(), any()))
                .thenReturn("{\"recommendations\":[]}");

        assertThat(recommendationService.recommendForUser(7L)).isEmpty();

        // Regression: `RecipeService.search` dereferences the request, so passing null
        // threw a NullPointerException that disabled-AI tests never reached.
        ArgumentCaptor<RecipeSearchRequest> request = ArgumentCaptor.forClass(RecipeSearchRequest.class);
        verify(recipeService).search(eq(7L), request.capture());
        assertThat(request.getValue()).isNotNull();
    }

    private static UserPreferencesResponse preferences(boolean useAi) {
        return new UserPreferencesResponse(FilterMode.LAX, (short) 50, Diet.BALANCED, ThemeId.BLUE, useAi);
    }
}
