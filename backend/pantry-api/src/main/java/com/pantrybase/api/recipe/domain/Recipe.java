package com.pantrybase.api.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A cooking recipe, either sourced from an external provider (TheMealDB) or created by the user.
 *
 * <p>The combination of {@code source} and {@code externalId} is unique: a provider recipe is
 * materialised once and then served from the local copy. User recipes carry {@code source = USER}
 * and a generated UUID as {@code externalId}.</p>
 */
@Entity
@Table(
        name = "recipes",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipes_source_external", columnNames = { "source", "external_id" }),
        indexes = { @Index(name = "idx_recipes_source", columnList = "source"), @Index(name = "idx_recipes_name", columnList = "name") }
)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source", nullable = false, length = 32)
    private String source;                 // THE_MEAL_DB | USER

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "area", length = 100)
    private String area;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "video_url", length = 512)
    private String videoUrl;

    @Column(name = "tags", length = 512)
    private String tags;

    @Column(name = "servings")
    private Integer servings;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- enum for source (kept as String in DB, but type-safe here) ---
    public enum Source { THE_MEAL_DB, USER }

    public Recipe() {}

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Source getSourceEnum() {
        return source != null ? Source.valueOf(source) : null;
    }
    public void setSourceEnum(Source s) {
        this.source = s != null ? s.name() : null;
    }
}