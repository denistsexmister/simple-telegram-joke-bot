package com.mrlang.telegramjokebot.service;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mrlang.telegramjokebot.config.BotConfig;
import com.mrlang.telegramjokebot.model.*;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;
    final UserRepository userRepository;
    final JokeRepository jokeRepository;

    static final String HELP_TEXT = """
            This bot can send you some random joke.

            You can execute commands from the main menu or by typing a command:

            Type /joke to get random joke
            
            Type /settings to set bot preferences(e.g. bot can send new message or edit the existing one by clicking "next joke" button, etc.)

            Type /help to see this message again""";
    static final String GET_NEXT_RANDOM_JOKE = "NEXT_RANDOM_JOKE";
    static final String CHANGE_EMBEDDED_JOKE_SETTINGS = "CHANGE_EMBEDDED_JOKE_SETTINGS";
    final int MAX_JOKE_ID_MINUS_ONE;

    @Autowired
    public TelegramBot(BotConfig config, UserRepository userRepository, JokeRepository jokeRepository) {
        super(config.getToken());
        this.config = config;
        this.userRepository = userRepository;
        this.jokeRepository = jokeRepository;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/joke", "get a random joke"));
        listOfCommands.add(new BotCommand("/settings", "set bot preferences"));
        listOfCommands.add(new BotCommand("/help", "show command info and usages"));


        try {
            execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }

        if (!jokeRepository.findAll().iterator().hasNext()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.addMixIn(Joke.class, JokeMixinIgnoreId.class);

                TypeFactory typeFactory = mapper.getTypeFactory();
                List<Joke> jokes = mapper.readValue(new File("db/jokes.json"),
                        typeFactory.constructCollectionType(List.class, Joke.class));

                jokes = jokes.stream().filter(joke -> !joke.getBody().isBlank()).toList();

                jokeRepository.saveAll(jokes);
            } catch (IOException e) {
                log.error(Arrays.toString(e.getStackTrace()));
            }
        }

        MAX_JOKE_ID_MINUS_ONE = (int) (StreamSupport.stream(jokeRepository.findAll().spliterator(), false).count() - 1);
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
                case "/start" -> showStart(msg);
                case "/joke" -> sendRandomJoke(msg.getChatId());
                case "/settings" -> showSettings(msg.getChatId());
                case "/help" -> showHelp(msg.getChatId());
                default -> commandNotFound(msg.getChatId());
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            long chatId = callbackQuery.getMessage().getChatId();
            int messageId = ((Message) callbackQuery.getMessage()).getMessageId();
            Optional<User> possibleUser = userRepository.findById(chatId);
            if (possibleUser.isEmpty()) {
                log.error("User was not found in database!");
                return;
            }
            User user = possibleUser.get();

            if (callbackQuery.getData().equals(GET_NEXT_RANDOM_JOKE)) {
                Optional<Joke> possibleJoke = getRandomJoke();
                if (!user.getEmbedeJoke()) {
                     possibleJoke.ifPresentOrElse(joke -> addButtonAndEditMessage(joke.getBody(), chatId, messageId),
                            () -> addButtonAndEditMessage("Can't get random joke for some reason, please try again", chatId, messageId));

                } else {
                    possibleJoke.ifPresentOrElse(joke -> addButtonAndSendMessage(joke.getBody(), chatId),
                            () -> addButtonAndSendMessage("Can't get random joke for some reason, please try again", chatId));
                }



            } else if (callbackQuery.getData().equals(CHANGE_EMBEDDED_JOKE_SETTINGS)) {
                user.setEmbedeJoke(!user.getEmbedeJoke());
                userRepository.save(user);
                showSettingsEditMessage(chatId, messageId);
            }
        }
    }

    private void showSettingsEditMessage(long chatId, int messageId) {
        Optional<User> possibleUser = userRepository.findById(chatId);
        if (possibleUser.isEmpty()) {
            log.error("Tried to get user, but got null!");
            return;
        }

        User user = possibleUser.get();
        StringBuilder textBuilder = new StringBuilder("Current settings\n\nOn click \"next joke\": ");
        String embeddedJokeButtonText;

        if (user.getEmbedeJoke()) {
            textBuilder.append("new message");
            embeddedJokeButtonText = "On click \"next joke\" set edit message";
        } else {
            textBuilder.append("edit existing message");
            embeddedJokeButtonText = "On click \"next joke\" set new message";
        }
        textBuilder.append("\n\n");
        EditMessageText sendMessage = EditMessageText.builder()
                .text(textBuilder.toString())
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(new InlineKeyboardMarkup(
                        List.of(List.of(InlineKeyboardButton.builder()
                                .text(EmojiParser.parseToUnicode(embeddedJokeButtonText))
                                .callbackData(CHANGE_EMBEDDED_JOKE_SETTINGS)
                                .build()))))
                .build();

        send(sendMessage);
    }

    private void showSettings(long chatId) {
        Optional<User> possibleUser = userRepository.findById(chatId);
        if (possibleUser.isEmpty()) {
            log.error("Tried to get user, but got null!");
            return;
        }

        User user = possibleUser.get();
        StringBuilder textBuilder = new StringBuilder("Current settings\n\nOn click \"next joke\": ");
        String embeddedJokeButtonText;

        if (user.getEmbedeJoke()) {
            textBuilder.append("new message");
            embeddedJokeButtonText = "On click \"next joke\" set edit message";
        } else {
            textBuilder.append("edit existing message");
            embeddedJokeButtonText = "On click \"next joke\" set new message";
        }
        textBuilder.append("\n\n");
        SendMessage sendMessage = SendMessage.builder()
                .text(textBuilder.toString())
                .chatId(chatId)
                .replyMarkup(new InlineKeyboardMarkup(
                        List.of(List.of(InlineKeyboardButton.builder()
                                .text(EmojiParser.parseToUnicode(embeddedJokeButtonText))
                                .callbackData(CHANGE_EMBEDDED_JOKE_SETTINGS)
                                .build()))))
                .build();

        send(sendMessage);
    }

    private void sendRandomJoke(long chatId) {
        Optional<Joke> possibleJoke = getRandomJoke();

        possibleJoke.ifPresentOrElse(joke -> addButtonAndSendMessage(joke.getBody(), chatId),
                () -> addButtonAndSendMessage("Can't get random joke for some reason, please try again", chatId));
    }

    private void showHelp(long chatId) {
        String answer = EmojiParser.parseToUnicode(HELP_TEXT);
        sendMessage(answer, chatId);
    }

    private void addButtonAndEditMessage(String joke, long chatId, int messageId) {
        EditMessageText sendMessage = EditMessageText.builder()
                .text(joke)
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(new InlineKeyboardMarkup(
                        List.of(List.of(InlineKeyboardButton.builder()
                                .text(EmojiParser.parseToUnicode("Next joke " + ":rolling_on_the_floor_laughing:"))
                                .callbackData(GET_NEXT_RANDOM_JOKE)
                                .build()))))
                .build();

        send(sendMessage);
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

    private void send(EditMessageText editMessage) {
        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    private void showStart(Message msg) {
        if (!userRepository.existsById(msg.getChatId())) {
            userRepository.save(mapUser(msg));
        }
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + msg.getChat().getFirstName() + "! :smile:" + " Nice to meet you! I am a Simple Random Joke Bot\n");
        sendMessage(answer, msg.getChatId());
    }

    private User mapUser(Message msg) {
        User user = new User();

        user.setChatId(msg.getChatId());
        user.setEmbedeJoke(false);

        return user;
    }

}
