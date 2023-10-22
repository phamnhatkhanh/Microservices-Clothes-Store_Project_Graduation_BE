package com.clothesstore.productservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;


@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("body_html")
    private String bodyHtml;

    @JsonProperty("vendor")
    private String vendor;

    @JsonProperty("product_type")
    private String productType;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("handle")
    private String handle;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("published_at")
    private String publishedAt;

    @JsonProperty("template_suffix")
    private String templateSuffix;

    @JsonProperty("published_scope")
    private String publishedScope;

    @JsonProperty("tags")
    private String tags;

    @JsonProperty("status")
    private String status;
//    private List<Address> addresses;


}