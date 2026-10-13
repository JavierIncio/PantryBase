/**
 * AI data models exposed by the `/api/ai` endpoints (roadmap H7).
 *
 * Hand-written DTOs mirroring `RecommendationService.RecommendationResponse`
 * one to one, like the recipe and user models; like them they are stand-ins to
 * be replaced by the generated OpenAPI client once the springdoc spec is wired
 * into the build.
 */

/**
 * One LLM suggestion, as `POST /api/ai/recommend` answers it.
 *
 * The recommendation never invents a recipe: `recipeId` always points at a row
 * of the local catalog (the backend constrains the model to ids it was given),
 * so it is safe to link straight to `/recipes/{recipeId}`. `title` is the
 * recipe's name at generation time and is rendered as-is; `reasoning` is free
 * text the model wrote about why the pantry fits, and it is the only field the
 * UI does not cross-check.
 *
 * `coveragePercentage` is already a 0-100 integer (not the 0..1 fraction of
 * {@link RecipeSummary.matchScore}), which is why the grid maps it back through
 * the shared coverage colors instead of using `coveragePercent`.
 */
export interface Recommendation {
  /** Internal recipe id; always an existing catalog recipe to link to. */
  readonly recipeId: number;
  readonly title: string;
  /** Pantry coverage the model computed, 0-100. */
  readonly coveragePercentage: number;
  /** Ingredients the recipe needs that the pantry is short of; may be empty. */
  readonly missingIngredients: string[];
  /** Free-text justification written by the model. */
  readonly reasoning: string;
}
