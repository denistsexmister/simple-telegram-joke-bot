package com.mrlang.telegramjokebot.service;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mrlang.telegramjokebot.config.BotConfig;
import com.mrlang.telegramjokebot.model.Joke;
import com.mrlang.telegramjokebot.model.JokeRepository;
import com.mrlang.telegramjokebot.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;
    final UserRepository userRepository;
    final JokeRepository jokeRepository;

    static final String HELP_TEXT = """
            This bot is created to demonstrate Spring capabilities.

            You can execute commands from the main menu or by typing a command:

            Type /start to see a welcome message

            Type /mydata to see stored data about yourself
            
            Type /deletedata to delete stored data about yourself
            
            Type /settings to set bot preferences

            Type /help to see this message again""";
    static final int MAX_JOKE_ID_MINUS_ONE = 3772;
    static final String GET_NEXT_RANDOM_JOKE = "NEXT_RANDOM_JOKE";

    @Autowired
    public TelegramBot(BotConfig config, UserRepository userRepository, JokeRepository jokeRepository) {
        super(config.getToken());
        this.config = config;
        this.userRepository = userRepository;
        this.jokeRepository = jokeRepository;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/joke", "get a random joke"));
        listOfCommands.add(new BotCommand("/help", "show command info and usages"));
        listOfCommands.add(new BotCommand("/settings", "set bot preferences"));

        try {
            execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public String getBotUsername() {
        return config.getName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message msg = update.getMessage();
            switch (msg.getText()) {
                case "/start" -> {
                    showStart(msg.getChatId(), msg.getChat().getFirstName());
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        TypeFactory typeFactory = mapper.getTypeFactory();
                        List<Joke> jokes = mapper.readValue(new File("db/jokes.json"),
                                typeFactory.constructCollectionType(List.class, Joke.class));
                        jokeRepository.saveAll(jokes);
                    } catch (IOException e) {
                        log.error(Arrays.toString(e.getStackTrace()));
                    }
                }
                case "/joke" -> {
                    Optional<Joke> possibleJoke = getRandomJoke();

                    possibleJoke.ifPresentOrElse(joke -> addButtonAndSendMessage(joke.getBody(), msg.getChatId()),
                            () -> addButtonAndSendMessage("Can't get random joke for some reason, please try again", msg.getChatId()));
                }
                default -> commandNotFound(msg.getChatId());
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            long chatId = callbackQuery.getMessage().getChatId();

            if (callbackQuery.getData().equals(GET_NEXT_RANDOM_JOKE)) {
                Optional<Joke> possibleJoke = getRandomJoke();

                possibleJoke.ifPresentOrElse(joke -> addButtonAndSendMessage(joke.getBody(), chatId),
                        () -> addButtonAndSendMessage("Can't get random joke for some reason, please try again", chatId));
            }
        }
    }

    private void addButtonAndSendMessage(String joke, long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .text(joke)
                .chatId(chatId)
                .replyMarkup(new InlineKeyboardMarkup(
                        List.of(List.of(InlineKeyboardButton.builder()
                                .text(EmojiParser.parseToUnicode("Next joke " + ":rolling_on_the_floor_laughing:"))
                                .callbackData(GET_NEXT_RANDOM_JOKE)
                                .build()))))
                .build();

        send(sendMessage);
    }

    private Optional<Joke> getRandomJoke() {
        Random random = new Random();
        int randomId = random.nextInt(MAX_JOKE_ID_MINUS_ONE) + 1;

        return jokeRepository.findById(randomId);
    }

    private void commandNotFound(long chatId) {
        String answer = EmojiParser.parseToUnicode(
                "Command not recognized, please verify and try again :stuck_out_tongue_winking_eye: ");
        sendMessage(answer, chatId);
    }

    private void sendMessage(String textToSend, long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(textToSend)
                .build();

        send(sendMessage);
    }

    private void send(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smile:" + " Nice to meet you! I am a Simple Random Joke Bot\n");
        sendMessage(answer, chatId);
    }

}
