package com.mrlang.telegramjokebot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Joke {
    @Id
    @GeneratedValue
    private Integer id;
    @Column(columnDefinition = "text")
    private String body;
    @Column
    private String category;
    @Column
    private double rating;
}
