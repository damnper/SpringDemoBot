package io.proj3ct.SpringDemoBot.service;

import io.proj3ct.SpringDemoBot.config.BotConfig;
import io.proj3ct.SpringDemoBot.model.User;
import io.proj3ct.SpringDemoBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;


@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    final BotConfig config;

    static final String HELP_TEXT = """
            Этот бот создан для рассылки сообщений.

            Вы можете выполнить команды из основного меню слева или ввести команду вручную:

            Нажмите /start для главного меню

            Нажмите /subscribe, чтобы оформить подписку на рассылку новостей и уведомлений

            Нажмите /delete, чтобы удалить свои данные и отписаться от рассылки новостей и уведомлений""";

    static final String COMMAND_NOT_EXIST_TEXT = "Извините, не удалось распознать команду. Если у Вас есть вопросы или вам нужна помощь, введите /help.";
    static final String SUBSCRIBE_TRUE_TEXT = "Спасибо за подписку! Теперь Вы будете получать уведомления от нашего бота. Оставайтесь в курсе последних новостей и событий.";
    static final String SUBSCRIBE_FALSE_TEXT = "Понимаем ваш выбор. Если в будущем Вы решите подписаться на уведомления, Мы будем готовы предоставить вам актуальную информацию. Спасибо за внимание!";
    static final String UNSUBSCRIBE_TRUE_TEXT = """
            Успешная отписка.
                        
            Вы больше не будете получать уведомления от нашего бота. Если вы решите вернуться, Мы всегда здесь для Вас! В случае вопросов или если у Вас возникнут какие-либо проблемы, не стесняйтесь связаться с нами.
                        
            Спасибо за то, что были с нами!""";
    static final String UNSUBSCRIBE_FALSE_TEXT = """
            Спасибо, что остаетесь с нами!
                        
            Ваш выбор не отписываться от наших уведомлений очень важен для нас. Если у вас возникнут вопросы или предложения, не стесняйтесь сообщить нам. Мы всегда здесь для вас!
            """;

    static final String SUBSCRIBE_QUESTION_TEXT = "Вы хотите подписаться на рассылку уведомлений?";
    static final String UNSUBSCRIBE_QUESTION_TEXT = "Вы хотите отписаться от рассылки уведомлений?";
    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";
    static final String CHOOSE_ROOM_COUNT = "Выберите количество комнат: ";
    static final String ONE_ROOM_BUTTON = "ONE_ROOM_BUTTON";
    static final String TWO_ROOM_BUTTON = "TWO_ROOM_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";
    static final String USER_ALREADY_REGISTERED_TEXT = "Эй! Вы уже зарегистрированы у нас. \uD83E\uDD16\uD83C\uDF1F";
    static final String NOT_SUBSCRIBED_WANT_TO_UNSUBSCRIBE_TEXT = "Эй! Вы еще не подписаны на наши уведомления. \uD83E\uDD16❌";



    private List<Pair<String, String>> checkCian() {

        List<Pair<String, String>> foundApartments = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://kazan.cian.ru/snyat-kvartiru-1-komn-ili-2-komn/")
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (Windows; U; Windows NT 6.1; rv:2.2) Gecko/20110201")
                    .referrer("http://www.google.com")
                    .timeout(12000)
                    .followRedirects(true)
                    .get();

            // Проверка объявлений на длительный срок аренды и отправка уведомлений
            String description = Objects.requireNonNull(doc.selectFirst("span._93444fe79c--color_black_100--Ephi7")).text();
            String link = Objects.requireNonNull(doc.selectFirst("a._93444fe79c--link--VtWj6")).attr("href");
            String date = Objects.requireNonNull(doc.selectFirst("div._93444fe79c--absolute--yut0v span._93444fe79c--text_letterSpacing__normal--tfToq")).text();

            if (date.contains("сегодня")) {

                foundApartments.add(Pair.of(description, link));
                log.info("Успешно проверено и добавлено объявление: {}", description);
                return foundApartments;
            }


        } catch (IOException e) {
            log.error("Ошибка при попытке подключения к сайту CIAN: " + e.getMessage());
        }

        return Collections.emptyList();
    }

    private boolean checkApartments(List<Pair<String, String>> foundApartments) {

        String apartmentType = "квартира";

        for (Pair<String, String> apartment : foundApartments) {
            String description = apartment.getLeft();
            if (description.contains(apartmentType)) {
                return true;
            }
        }
        return false;
    }


    @Scheduled(fixedDelay = 6000)
    private void executeCian() {

        List<Pair<String, String>> foundApartments = checkCian();

        if (!foundApartments.isEmpty() && checkApartments(foundApartments)) {
            sendNotification(foundApartments);
        }
    }

    private void sendNotification(List<Pair<String, String>> foundApartments) {


        List<User> subscribedUsers = (List<User>) userRepository.findAll();

        for (User user : subscribedUsers) {
            Long chatId = user.getChatId();
            SendMessage message = getSendMessage(foundApartments, user, chatId);
            try {
                if(message != null && !message.getChatId().isEmpty()) {
                    execute(message);
                    log.info("Уведомление успешно отправлено, id: {}", chatId);
                }
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке уведомления: " + e.getMessage());
            }
        }
        userRepository.saveAll(subscribedUsers);
    }

    private static SendMessage getSendMessage(List<Pair<String, String>> foundApartments, User user, Long chatId) {

        String apartment_type = user.getApartmentType();

        SendMessage message = new SendMessage();

        for (Pair<String, String> apartment : foundApartments) {
            String description = apartment.getLeft();
            String link = apartment.getRight();

            String lastPostedLink = user.getApartmentLink();

            if(String.valueOf(description.charAt(0)).equals(apartment_type)) {
                if(chatId != null && !Objects.equals(lastPostedLink, link)) {
                    message.setChatId(String.valueOf(chatId));
                    message.setText("Новая квартира: " + description + "\nСсылка: " + link);

                    user.setApartmentLink(link);
                    user.setApartmentDescription(description);

                    return message;
                }
            } else {
                return null;
            }
        }
        return null;
    }

    private void setupApartmentMonitoringForm(Message msg) {

        long chatId = msg.getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
        message.setText(CHOOSE_ROOM_COUNT); // Устанавливаем текст сообщения

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = getRoomCountKeyboardMarkup();
        // Устанавливаем встроенную клавиатуру для сообщения
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        // Отправляем сообщение
        executeMessage(message);
    }

    private static List<List<InlineKeyboardButton>> getRoomCountKeyboardMarkup() {
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInlineOne = new ArrayList<>();
        // Создаем кнопку "1"
        var yesButton = new InlineKeyboardButton();
        yesButton.setText("1");
        yesButton.setCallbackData(ONE_ROOM_BUTTON);
        // Добавляем кнопки в строку
        rowInlineOne.add(yesButton);

        List<InlineKeyboardButton> rowInlineTwo = new ArrayList<>();
        // Создаем кнопку "2"
        var noButton = new InlineKeyboardButton();
        noButton.setText("2");
        noButton.setCallbackData(TWO_ROOM_BUTTON);
        rowInlineTwo.add(noButton);
        // Добавляем строку с кнопками
        rowsInline.add(rowInlineOne);
        rowsInline.add(rowInlineTwo);
        return rowsInline;
    }

    public TelegramBot(BotConfig config) {
        this.config = config;
        initializeCommands();
    }

    private void initializeCommands() {
        List<BotCommand> listOfCommands = List.of(
                new BotCommand("/start", "Главное меню"),
                new BotCommand("/subscribe", "Подписаться на рассылку"),
                new BotCommand("/delete", "Отписаться от уведомлений и удалить свои данные"),
                new BotCommand("/cian", "функция Циан"),
                new BotCommand("/help", "Помощь по управлению ботом")
        );
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
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

    @Override
    public void onUpdateReceived(Update update) {
        if (isTextMessage(update)) {
            try {
                processTextMessage(update);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (isCallbackQuery(update)) {
            processCallbackQuery(update);
        }
    }

    private boolean isTextMessage(Update update) {
        return update.hasMessage() && update.getMessage().hasText();
    }

    private boolean isCallbackQuery(Update update) {
        return update.hasCallbackQuery();
    }

    private void processTextMessage(Update update) throws IOException {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        if (messageText.startsWith("/send") && config.getOwnerId() == chatId) {
            processSendCommand(messageText);
        } else {
            processUserCommand(messageText, chatId, update);
        }
    }

    private void processSendCommand(String messageText) {
        var textToSend = messageText.substring(messageText.indexOf(" ")); // Забираем все сообщение после /send
        var users = userRepository.findAll(); // Забираем из БД всех пользователей
        for (User user : users) {
            prepareAndSendMessage(user.getChatId(), textToSend); // каждому пользователю из БД отправляем сообщение
        }
    }

    private void processUserCommand(String messageText, long chatId, Update update) throws IOException {
        switch (messageText) {
            case "/start":
                startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                break;
            case "/subscribe", "Подписаться":
                register(update.getMessage());
                break;
            case "/delete", "Отписаться":
                delete(update.getMessage());
                break;
            case "/cian":
                setupApartmentMonitoringForm(update.getMessage());
                break;
            case "Вывести мои данные":
                showMyData(update.getMessage());
                break;
            case "/help":
                prepareAndSendMessage(chatId, HELP_TEXT);
                break;
            default:
                prepareAndSendMessage(chatId, COMMAND_NOT_EXIST_TEXT);
        }
    }

    private void processCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String messageText = update.getCallbackQuery().getMessage().getText();

        switch (callbackData) {
            case YES_BUTTON -> processYesButton(update, messageText);
            case NO_BUTTON -> processNoButton(messageText, chatId, messageId);
            case ONE_ROOM_BUTTON, TWO_ROOM_BUTTON -> processRoomCountButton(update);
        }
    }

    private void processYesButton(Update update, String messageText) {

        if (messageText.equals(SUBSCRIBE_QUESTION_TEXT)) {
            registerUser(update.getCallbackQuery().getMessage());
        } else if (messageText.equals(UNSUBSCRIBE_QUESTION_TEXT)) {
            deleteUserData(update.getCallbackQuery().getMessage());
        }
    }

    private void processNoButton(String messageText, long chatId, long messageId) {

        if (messageText.equals(SUBSCRIBE_QUESTION_TEXT)) {
            executeEditMessageText(SUBSCRIBE_FALSE_TEXT, chatId, messageId);
        } else if (messageText.equals(UNSUBSCRIBE_QUESTION_TEXT)) {
            executeEditMessageText(UNSUBSCRIBE_FALSE_TEXT, chatId, messageId);
        }
    }

    private void processRoomCountButton(Update update) {

        Optional<User> optionalUser = userRepository.findById(update.getCallbackQuery().getMessage().getChatId());

        if(optionalUser.isPresent()) {
            User user = optionalUser.get();
            try {
                if (update.getCallbackQuery().getData().contains(ONE_ROOM_BUTTON)) {
                    user.setApartmentType("1");
                } else if (update.getCallbackQuery().getData().contains(TWO_ROOM_BUTTON)) {
                    user.setApartmentType("2");
                }
                executeEditMessageText("Форма заполнена!", update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId());
                userRepository.save(user);
                log.info("Apartment's type saved: " + user);
            } catch (DataAccessException e) {
                log.error("Error saving apartment's type for user: {}", e.getMessage());
            }
        }
    }

    private void registerUser(Message msg) {

        long chatId = msg.getChatId();
        var chat = msg.getChat();

        User user = new User();

        // Заполняем данные пользователя
        user.setChatId(chatId);
        user.setFirstName(chat.getFirstName());
        user.setLastName(chat.getLastName());
        user.setUserName(chat.getUserName());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

        // Сохраняем пользователя в базе данных
        try {
            userRepository.save(user);
            executeEditMessageText(SUBSCRIBE_TRUE_TEXT, chatId, msg.getMessageId());
            log.info("User saved: {}", user);

            setupApartmentMonitoringForm(msg);
        } catch (DataAccessException e) {
            log.error("Error saving user to the database: {}", e.getMessage());
        }
    }

    private boolean isUserInDB(Message msg) { return userRepository.findById(msg.getChatId()).isPresent(); }

    private void register(Message msg) {

        if (!isUserInDB(msg)) {
            long chatId = msg.getChatId();
            // Создаем новое сообщение
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
            message.setText("Вы хотите подписаться на рассылку уведомлений?"); // Устанавливаем текст сообщения


            // Создаем встроенную клавиатуру
            InlineKeyboardMarkup markupInline = getYesNoKeyboardMarkup();
            message.setReplyMarkup(markupInline);
            // Отправляем сообщение
            executeMessage(message);
        } else {
            prepareAndSendMessage(msg.getChatId(), USER_ALREADY_REGISTERED_TEXT);
            log.warn("User already exist: {}", msg.getChat().getFirstName() + " " + msg.getChat().getLastName());
        }
    }

    private static InlineKeyboardMarkup getYesNoKeyboardMarkup() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        // Создаем кнопку "Да"
        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData(YES_BUTTON);
        // Создаем кнопку "Нет"
        var noButton = new InlineKeyboardButton();
        noButton.setText("Нет");
        noButton.setCallbackData(NO_BUTTON);
        // Добавляем кнопки в строку
        rowInline.add(yesButton);
        rowInline.add(noButton);
        // Добавляем строку с кнопками
        rowsInline.add(rowInline);
        // Устанавливаем встроенную клавиатуру для сообщения
        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    private void deleteUserData(Message msg) {

        Optional<User> optionalUser = userRepository.findById(msg.getChatId());
        // Если пользователь с таким chatId найден, выполняем удаление пользователя из базы данных
        if(optionalUser.isPresent()) {
            User user = optionalUser.get();
            // Удаляем пользователя из базы данных
            try {
                userRepository.delete(user);
                executeEditMessageText(UNSUBSCRIBE_TRUE_TEXT, msg.getChatId(), msg.getMessageId());
                log.info("User deleted: " + user);
            } catch (DataAccessException e) {
                log.error("Error delete user from the database: {}", e.getMessage());
            }
        }
    }

    private void delete(Message msg) {

        long chatId = msg.getChatId();
        if (isUserInDB(msg)) {
            // Создаем новое сообщение
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
            message.setText("Вы хотите отписаться от рассылки уведомлений?"); // Устанавливаем текст сообщения

            // Создаем встроенную клавиатуру

            InlineKeyboardMarkup markupInline = getYesNoKeyboardMarkup();
            message.setReplyMarkup(markupInline);
            // Отправляем сообщение
            executeMessage(message);
        } else {
            prepareAndSendMessage(msg.getChatId(), NOT_SUBSCRIBED_WANT_TO_UNSUBSCRIBE_TEXT);
            log.warn("User not found for chatId: " + chatId);
        }
    }

    private void showMyData(Message msg) {

        long chatId = msg.getChatId();
        Optional<User> optionalUser = userRepository.findById(chatId);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            // Логика отображения данных пользователя
            prepareAndSendMessage(chatId, "Ваши данные:\n" + user.getUserDataDB());
        } else {

            prepareAndSendMessage(chatId, "Вы не подписаны на бота!");
            log.warn("User not found for chatId: {}", chatId);
        }
    }

    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText(); // Создаем новое сообщение для редактирования
        // Устанавливаем chatId, текст и messageId для редактируемого сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);

        try {
            execute(message); // Пытаемся выполнить редактирование сообщения
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage()); // В случае ошибки при редактировании логируем сообщение об ошибке
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message); // Пытаемся выполнить отправку сообщения
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage()); // В случае ошибки при отправке логируем сообщение об ошибке
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = "Добро пожаловать, " + name + "! Мы рады приветствовать вас."; // Формируем ответное сообщение
        log.info("Replied to user " + name); // Логируем информацию о том, что был отправлен ответ пользователю
        sendMessage(chatId, answer); // Отправляем ответное сообщение пользователю
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage(); // Создаем новое сообщение
        // Устанавливаем chatId и текст сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        // Создаем объект для настраиваемой клавиатуры
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        // Создаем список строк клавиатуры
        List<KeyboardRow> keyboardRows = getKeyboardRows();
        // Устанавливаем список строк клавиатуры в объект клавиатуры
        keyboardMarkup.setKeyboard(keyboardRows);
        // Устанавливаем настраиваемую клавиатуру в сообщение
        message.setReplyMarkup(keyboardMarkup);
        // Отправляем сообщение
        executeMessage(message);
    }

    private static List<KeyboardRow> getKeyboardRows() {
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        // Создаем первую строку клавиатуры
        KeyboardRow row = new KeyboardRow();
        // Добавляем кнопки в первую строку
        row.add("Подписаться");
        row.add("Отписаться");
        // Добавляем первую строку в список строк клавиатуры
        keyboardRows.add(row);
        // Создаем вторую строку клавиатуры
        row = new KeyboardRow();
        // Добавляем кнопки во вторую строку
        row.add("Вывести мои данные");
        // Добавляем вторую строку в список строк клавиатуры
        keyboardRows.add(row);
        return keyboardRows;
    }

    private void prepareAndSendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage(); // Создаем новое сообщение
        // Устанавливаем chatId и текст сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message); // Вызываем метод для выполнения отправки сообщения
    }
}
