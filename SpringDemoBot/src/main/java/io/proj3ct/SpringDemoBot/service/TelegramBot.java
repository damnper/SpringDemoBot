package io.proj3ct.SpringDemoBot.service;

import io.proj3ct.SpringDemoBot.config.BotConfig;
import io.proj3ct.SpringDemoBot.model.Ads;
import io.proj3ct.SpringDemoBot.model.AdsRepository;
import io.proj3ct.SpringDemoBot.model.User;
import io.proj3ct.SpringDemoBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdsRepository adsRepository;
    final BotConfig config;

    static final String HELP_TEXT = """
            Этот бот создан для рассылки сообщений.

            Вы можете выполнить команды из основного меню слева или ввести команду вручную:

            Нажмите /start для главного меню

            Нажмите /subscribe, чтобы оформить подписку на рассылку новостей и уведомлений

            Нажмите /delete, чтобы удалить свои данные и отписаться от рассылки новостей и уведомлений""";

    static final String COMMAND_NOT_EXIST_TEXT = "Извините, не удалось распознать команду. Если у вас есть вопросы или вам нужна помощь, введите /help.";
    static final String SUBSCRIBE_TRUE_TEXT = "Спасибо за подписку! Теперь вы будете получать уведомления от нашего бота. Оставайтесь в курсе последних новостей и событий.";
    static final String SUBSCRIBE_FALSE_TEXT = "Понимаем ваш выбор. Если в будущем вы решите подписаться на уведомления, мы будем готовы предоставить вам актуальную информацию. Спасибо за внимание!";
    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";
    static final String ERROR_TEXT = "Error occurred: ";

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Главное меню"));
        listOfCommands.add(new BotCommand("/subscribe", "Подписаться на рассылку"));
        listOfCommands.add(new BotCommand("/delete", "Отписаться и удалить свои данные"));
        listOfCommands.add(new BotCommand("/help", "Помощь по управлению ботом"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e){
            log.error("Ошибка в установке команд бота: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    /*Этот метод вызывается при получении нового сообщения от пользователя
    Обработка полученного обновления (например, текстового сообщения, команды и т.д.)
    update содержит информацию о сообщении, которое было получено*/
    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()) { // Проверка: пришло ли сообщение и имеет ли сообщение текст
            String messageText = update.getMessage().getText(); // Текст сообщения

            long chatId = update.getMessage().getChatId(); // ID пользователя

            if (messageText.contains("/send") && config.getOwnerId() == chatId) { // Проверка: сообщение /send от владельца
                var textToSend = messageText.substring(messageText.indexOf(" ")); // Забираем все сообщение после /send
                var users = userRepository.findAll(); // Забираем из БД всех пользователей
                for (User user: users) {
                    prepareAndSendMessage(user.getChatId(), textToSend); // каждому пользователю из БД отправляем сообщение
                }
            } else {
                switch (messageText) {
                    case "/start": // запуск бота
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;
                    case "/subscribe": // подписаться на рассылку
                        register(chatId);
                        // registerUser(update.getMessage());
                        break;
                    case "/help": // инструкция
                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;
                    default:
                        prepareAndSendMessage(chatId, COMMAND_NOT_EXIST_TEXT);
                }
            }

        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId= update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(YES_BUTTON)) {
                executeEditMessageText(SUBSCRIBE_TRUE_TEXT, chatId, messageId);
                registerUser(update.getCallbackQuery().getMessage());

            } else if (callbackData.equals(NO_BUTTON)) {
                executeEditMessageText(SUBSCRIBE_FALSE_TEXT, chatId, messageId);
            }
        }
    }

    private void registerUser(Message msg) {

        if (userRepository.findById(msg.getChatId()).isEmpty()) {

            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("User saved: " + user);
        }
    }

    private void register(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Вы хотите подписаться на рассылку уведомлений?");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Да");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("Нет");
        noButton.setCallbackData(NO_BUTTON);

        rowInline.add(yesButton);
        rowInline.add(noButton);

        rowsInline.add(rowInline);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        executeMessage(message);

    }

    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void startCommandReceived(long chatId, String name) {

        String answer = "Добро пожаловать, " + name + "! Мы рады приветствовать вас.";

        log.info("Replied to user " + name);

        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        row.add("weather");
        row.add("get random joke");

        keyboardRows.add(row);

        row = new KeyboardRow();

        row.add("register");
        row.add("check my data");
        row.add("delete my data");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void prepareAndSendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    /*@Scheduled(cron = "${cron.scheduler}")
    private void sendAds() {
        var ads = adsRepository.findAll();
        var users = userRepository.findAll();

        for (Ads ad: ads) {
            for (User user: users) {
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }
        }
    }*/
}

