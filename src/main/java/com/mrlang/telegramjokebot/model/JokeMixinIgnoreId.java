package com.mrlang.telegramjokebot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"id"})
public abstract class JokeMixinIgnoreId {
}
