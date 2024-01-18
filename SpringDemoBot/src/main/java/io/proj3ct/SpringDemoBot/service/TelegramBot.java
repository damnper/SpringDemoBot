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
    private final UserRepository userRepository;

    final BotConfig config;

    private static final String HELP_TEXT = """
              Привет! 🌍🔎
                                                                                                      
              RoamAndHuntBot - Ваш личный ассистент в мире недвижимости! 🏡✨
              
              🔍 *Как это работает?*
              1. Нажмите /subscribe, чтобы подписаться на уведомления о новых квартирах.
              2. Выберите типы квартир, о которых Вы хотите получать уведомления. (Например, выберите "1" для квартир однокомнатного типа.)
              3. Вы можете выбрать несколько типов квартир. Нажмите "Завершить", когда закончите выбор.
              
              🚀 *Основные команды:*
              /subscribe - подписаться на уведомления о новых квартирах;
              /delete - отписаться от рассылки;
              /help - получить справку о моих командах и функциях.
              
              🔧 *Дополнительные команды:*
              - /addtype - добавить тип квартиры после подписки;
              - /deletetype - удалить тип квартиры.
              
              Теперь Вы в курсе! Давайте вместе искать Ваш новый уютный уголок! 🌟🏠""";

    private static final String COMMAND_NOT_EXIST_TEXT = "Извините, не удалось распознать команду. Если у Вас есть вопросы или вам нужна помощь, введите /help.";
    private static final String SUBSCRIBE_TRUE_TEXT = "Спасибо за подписку! Теперь Вы будете получать уведомления о новых квартирах от нашего бота. ";
    private static final String SUBSCRIBE_FALSE_TEXT = "Понимаем ваш выбор. Если в будущем Вы решите подписаться на уведомления, Мы будем готовы предоставить вам актуальную информацию. Спасибо за внимание!";
    private static final String UNSUBSCRIBE_TRUE_TEXT = """
            Успешная отписка.
                        
            Вы больше не будете получать уведомления от нашего бота.
            Спасибо за то, что были с нами!""";
    private static final String UNSUBSCRIBE_FALSE_TEXT = """
            Спасибо, что остаетесь с нами!
                        
            Ваш выбор не отписываться от наших уведомлений очень важен для нас.
            """;

    private static final String SUBSCRIBE_QUESTION_TEXT = "Вы хотите подписаться на рассылку уведомлений?";
    private static final String UNSUBSCRIBE_QUESTION_TEXT = "Вы хотите отписаться от рассылки уведомлений?";
    private static final String YES_BUTTON = "YES_BUTTON";
    private static final String NO_BUTTON = "NO_BUTTON";
    private static final String CHOOSE_ROOM_COUNT = "Выберите количество комнат: ";
    private static final String ROOM_BUTTON = "ROOM_BUTTON_";
    private static final String TEXT_ADD_TYPE = "Какой тип квартиры Вы хотите добавить?";
    private static final String TEXT_ADD_TYPE_WRONG = """
            Чтобы выполнить данную команду, сначала подпишитесь на уведомления, используя команду /subscribe.\uD83C\uDFE0✨
            
            В случае некорректного заполнения формы Вы сможете добавить нужные типы квартир с помощью команды /addtype или исключить ненужные с помощью /removetype. \uD83E\uDD16\uD83C\uDFE0""";

    private static final String TEXT_REMOVE_TYPE = "Какой тип квартиры Вы хотите исключить?";


    private static final String ERROR_TEXT = "Error occurred: ";
    private static final String USER_ALREADY_REGISTERED_TEXT = "Эй! Вы уже зарегистрированы у нас. \uD83E\uDD16\uD83C\uDF1F";
    private static final String NOT_SUBSCRIBED_WANT_TO_UNSUBSCRIBE_TEXT = "Эй! Вы еще не подписаны на наши уведомления. \uD83E\uDD16❌";

    private static final String LINK_CONNECT_TO_CIAN = "https://kazan.cian.ru/cat.php?deal_type=rent&engine_version=2&offer_type=flat&region=4777&room1=1&room2=1&room3=1&room4=1&room5=1&room6=1&room9=1&sort=creation_date_desc&totime=-2&type=4";
    private static final String SELECTOR_DESCRIPTION = "span._93444fe79c--color_black_100--Ephi7";
    private static final String SELECTOR_LINK_TO_SEND = "a._93444fe79c--link--VtWj6";

    private Pair<String, String> checkCian() {

        try {
            Document doc = Jsoup.connect(LINK_CONNECT_TO_CIAN)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (Windows; U; Windows NT 6.1; rv:2.2) Gecko/20110201")
                    .referrer("http://www.google.com")
                    .timeout(12000)
                    .followRedirects(true)
                    .get();

            // Проверка объявлений на длительный срок аренды и отправка уведомлений
            String description = Objects.requireNonNull(doc.selectFirst(SELECTOR_DESCRIPTION)).text();
            String link = Objects.requireNonNull(doc.selectFirst(SELECTOR_LINK_TO_SEND)).attr("href");

            log.info("Успешно проверено и добавлено объявление: {}", description);
            return Pair.of(description, link);

        } catch (IOException e) {
            log.error("Ошибка при попытке подключения к сайту CIAN: " + e.getMessage());
        }

        return null;
    }

    @Scheduled(fixedDelay = 10000)
    private void executeCian() {

        Pair<String, String> foundApartments = checkCian();

        if (Objects.nonNull(foundApartments)) {
            sendNotification(foundApartments);
        }
    }

    private void sendNotification(Pair<String, String> foundApartments) {

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

    private static SendMessage getSendMessage(Pair<String, String> foundApartments, User user, Long chatId) {

        if (foundApartments == null || chatId == null) {
            return null;
        }

        String description = foundApartments.getLeft();
        String link = foundApartments.getRight();

        if (!isApartmentTypePresent(user, description) || Objects.equals(user.getApartmentLink(), link)) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Новая квартира: " + description + "\nСсылка: " + link);

        user.setApartmentLink(link);
        user.setApartmentDescription(description);

        return message;

    }

    private static boolean isApartmentTypePresent(User user, String description) {

        String apartmentTypeFromDB = user.getApartmentType();
        return apartmentTypeFromDB != null && apartmentTypeFromDB.contains(String.valueOf(description.charAt(0)));
    }

    private void setupApartmentMonitoringForm(Message msg) {

        if (msg == null) {
            return;
        }

        long chatId = msg.getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
        message.setText(CHOOSE_ROOM_COUNT); // Устанавливаем текст сообщения

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        markupInline.setKeyboard(getRoomCountKeyboardMarkup());
        message.setReplyMarkup(markupInline);

        executeMessage(message);
    }

    private static List<List<InlineKeyboardButton>> getRoomCountKeyboardMarkup() {

        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Студия
        rowsInline.add(createInlineButtonRow("Студия", "Студия"));

        // Кнопки для чисел от 1 до 6
        for (int i = 1; i < 6; i++) { rowsInline.add(createInlineButtonRow(String.valueOf(i), String.valueOf(i))); }

        // Кнопка "Завершить"
        rowsInline.add(createInlineButtonRow("Завершить", "Завершить"));

        return rowsInline;
    }

    private static List<List<InlineKeyboardButton>> getRoomCountKeyboardMarkup(String apartmentTypeFromDB, boolean addFlag) {

        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        if (addFlag) {
            if (!apartmentTypeFromDB.contains("Студия")) {
                rowsInline.add(createInlineButtonRow("Студия", "Студия"));
            }

            for (int i = 1; i < 6; i++) {
                if (!apartmentTypeFromDB.contains(String.valueOf(i))) {
                    rowsInline.add(createInlineButtonRow(String.valueOf(i), String.valueOf(i)));
                }
            }
        } else {
            if (apartmentTypeFromDB.contains("Студия")) {
                rowsInline.add(createInlineButtonRow("Студия", "Студия"));
            }

            for (int i = 1; i < 6; i++) {
                if (apartmentTypeFromDB.contains(String.valueOf(i))) {
                    rowsInline.add(createInlineButtonRow(String.valueOf(i), String.valueOf(i)));
                }
            }
        }

        rowsInline.add(createInlineButtonRow("Завершить", "Завершить"));

        return rowsInline;
    }

    private static List<InlineKeyboardButton> createInlineButtonRow(String buttonText, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(ROOM_BUTTON + callbackData);

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        return row;
    }

    private void addType(Message msg) {

        long chatId = msg.getChatId();

        if(isUserInDB(msg)) {


            Optional<User> optionalUser = userRepository.findById(chatId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                String apartmentTypeFromDB = user.getApartmentType();

                prepareAndSendMessageWithReplyKeyboard(chatId, TEXT_ADD_TYPE, apartmentTypeFromDB, true);
            }
        } else {
            prepareAndSendMessage(chatId, TEXT_ADD_TYPE_WRONG);
            log.warn("User try /addtype but unsubscribe: {}", msg.getChat().getFirstName() + " " + msg.getChat().getLastName());
        }
    }

    private void removeType(Message msg) {
        long chatId = msg.getChatId();

        if (isUserInDB(msg)) {
            Optional<User> optionalUser = userRepository.findById(chatId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                String apartmentTypeFromDB = user.getApartmentType();

                prepareAndSendMessageWithReplyKeyboard(chatId, TEXT_REMOVE_TYPE, apartmentTypeFromDB, false);
            }
        } else {
            prepareAndSendMessage(chatId, TEXT_ADD_TYPE_WRONG);
            log.warn("User try /removetype but unsubscribe: {}", msg.getChat().getFirstName() + " " + msg.getChat().getLastName());
        }
    }

    public TelegramBot(UserRepository userRepository, BotConfig config) {
        this.userRepository = userRepository;
        this.config = config;
        initializeCommands();
    }

    private void initializeCommands() {
        List<BotCommand> listOfCommands = List.of(
                new BotCommand("/start", "Главное меню"),
                new BotCommand("/subscribe", "Подписаться на рассылку"),
                new BotCommand("/delete", "Отписаться от уведомлений и удалить свои данные"),
                new BotCommand("/help", "Помощь по управлению ботом"),
                new BotCommand("/addtype", "Добавить тип квартиры"),
                new BotCommand("/removetype", "Исключить тип квартиры")
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
            case "/addtype":
                addType(update.getMessage());
                break;
            case "/removetype":
                removeType(update.getMessage());
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
            case "ROOM_BUTTON_Студия", "ROOM_BUTTON_1", "ROOM_BUTTON_2", "ROOM_BUTTON_3", "ROOM_BUTTON_4", "ROOM_BUTTON_5", "ROOM_BUTTON_Завершить" -> processRoomCountButton(update);
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
        Set<String> validApartmentTypes = new HashSet<>(Arrays.asList("Студия", "1", "2", "3", "4", "5"));
        Optional<User> optionalUser = userRepository.findById(update.getCallbackQuery().getMessage().getChatId());

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            String roomNumberButton = update.getCallbackQuery().getData();
            String messageText = update.getCallbackQuery().getMessage().getText();

            try {
                String apartmentType = extractRoomType(roomNumberButton);
                assert apartmentType != null;

                if (messageText.equals(TEXT_REMOVE_TYPE)) {

                    if (apartmentType.equals("Завершить")) {
                        executeEditMessageText("Форма успешно изменена!", user.getChatId(), update.getCallbackQuery().getMessage().getMessageId());
                        log.info("Apartment's type changed: " + user);
                    } else {
                        String newApartmentType = getNewApartmentType(user, apartmentType);
                        user.setApartmentType(newApartmentType);

                        String newApartmentTypeFromDB = user.getApartmentType();
                        executeEditMessageTextWithReplyKeyboard(newApartmentTypeFromDB, TEXT_REMOVE_TYPE, user.getChatId(), update.getCallbackQuery().getMessage().getMessageId(), false);

                    }
                    userRepository.save(user);

                } else {
                    if (apartmentType.equals("Завершить")) {
                        executeEditMessageText("Форма заполнена!", user.getChatId(), update.getCallbackQuery().getMessage().getMessageId());
                        log.info("Apartment's type saved: " + user);
                    } else if (validApartmentTypes.contains(apartmentType)) {

                        if (user.getApartmentType() == null) {
                            user.setApartmentType(apartmentType);
                        } else {
                            String apartmentTypeFromDB = user.getApartmentType();
                            String newApartmentType = String.join(",", apartmentTypeFromDB, apartmentType);
                            user.setApartmentType(newApartmentType);
                        }

                        String apartmentTypeFromDB = user.getApartmentType();
                        executeEditMessageTextWithReplyKeyboard(apartmentTypeFromDB, CHOOSE_ROOM_COUNT, user.getChatId(), update.getCallbackQuery().getMessage().getMessageId(), true);

                    } else {
                        log.error("Unsupported room number button: {}", roomNumberButton);
                    }

                    userRepository.save(user);
                }

            } catch (DataAccessException e) {
                log.error("Error saving apartment's type for user: {}", e.getMessage());
            }
        }
    }

    private static String getNewApartmentType(User user, String apartmentType) {
        String apartmentTypeFromDB = user.getApartmentType();
        String newApartmentType;

        if (apartmentTypeFromDB.charAt(apartmentTypeFromDB.length() - 1) == apartmentType.charAt(0)) {
            newApartmentType = apartmentTypeFromDB.replace(apartmentType, "");

        } else {
            newApartmentType = apartmentTypeFromDB.replace(apartmentType + ",", "");
        }
        return newApartmentType;
    }

    private String extractRoomType(String roomNumberButton) {
        // Извлекаем номер комнаты из текста callbackData
        String[] parts = roomNumberButton.split("_");
        if (parts.length == 3 && parts[0].equals("ROOM") && parts[1].equals("BUTTON")) {
            return parts[2];
        }
        return null;
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

    private void executeEditMessageTextWithReplyKeyboard(String apartmentTypeFromDB, String text, long chatId, long messageId, boolean addFlag) {
        EditMessageText message = new EditMessageText(); // Создаем новое сообщение для редактирования
        // Устанавливаем chatId, текст и messageId для редактируемого сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);
        
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = getRoomCountKeyboardMarkup(apartmentTypeFromDB, addFlag);
        // Устанавливаем встроенную клавиатуру для сообщения
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
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

    private void prepareAndSendMessageWithReplyKeyboard(long chatId, String textToSend, String apartmentTypeFromDB, boolean addFlag) {
        SendMessage message = new SendMessage(); // Создаем новое сообщение
        // Устанавливаем chatId и текст сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = getRoomCountKeyboardMarkup(apartmentTypeFromDB, addFlag);
        // Устанавливаем встроенную клавиатуру для сообщения
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        try {
            execute(message); // Пытаемся выполнить редактирование сообщения
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage()); // В случае ошибки при редактировании логируем сообщение об ошибке
        }
    }
}
