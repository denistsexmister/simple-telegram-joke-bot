package com.mrlang.telegramjokebot.model;

import org.springframework.data.repository.CrudRepository;

public interface JokeRepository extends CrudRepository<Joke, Integer> {
}
