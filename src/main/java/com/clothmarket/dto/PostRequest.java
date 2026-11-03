package com.clothmarket.dto;

import lombok.Data;

@Data
public class PostRequest {
    private String description;
    private Double price;
    private Integer quantity;
    private String clothType;
}
