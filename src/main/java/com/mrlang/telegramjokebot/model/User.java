package com.mrlang.telegramjokebot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity(name = "usersDataTable")
public class User {
    @Id
    private Long chatId;
    @Column
    private Boolean embedeJoke;
    @Column
    private String phoneNumber;
    @Column
    private String firstName;
    @Column
    private String lastName;
    @Column
    private String userName;
    @Column
    private Timestamp registeredAt;
    @Column
    private Double latitude;
    @Column
    private Double longitude;
    @Column
    private String bio;
    @Column
    private String description;
    @Column
    private String pinnedMessage;
}
