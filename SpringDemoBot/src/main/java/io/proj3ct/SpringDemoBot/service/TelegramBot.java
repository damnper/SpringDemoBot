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
import java.util.Objects;
import java.util.Optional;

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

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";
    static final String ERROR_TEXT = "Error occurred: ";

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Главное меню"));
        listOfCommands.add(new BotCommand("/subscribe", "Подписаться на рассылку"));
        listOfCommands.add(new BotCommand("/delete", "Отписаться от уведомлений и удалить свои данные"));
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
                    case "/delete": // отписаться от рассылки
                        delete(chatId);
                        break;
                    case "/help": // инструкция
                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;
                    default:
                        prepareAndSendMessage(chatId, COMMAND_NOT_EXIST_TEXT);
                }
            }

        } else if (update.hasCallbackQuery()) { // Проверяем, является ли обновление callback-запросом
            String callbackData = update.getCallbackQuery().getData(); // Получаем данные из callback-запроса (нажатия на кнопку)
            long messageId = update.getCallbackQuery().getMessage().getMessageId(); // Получаем идентификатор сообщения, в котором была кнопка
            long chatId= update.getCallbackQuery().getMessage().getChatId(); // Получаем идентификатор чата, в котором произошло событие
            String messageText = update.getCallbackQuery().getMessage().getText();
            // Проверяем, какая кнопка была нажата
            if (callbackData.equals(YES_BUTTON)) { // Если была нажата кнопка "Да"
                if (Objects.equals(messageText, "Вы хотите подписаться на рассылку уведомлений?")) {
                    executeEditMessageText(SUBSCRIBE_TRUE_TEXT, chatId, messageId); // Изменяем текст сообщения на подтверждение подписки
                    registerUser(update.getCallbackQuery().getMessage()); // Регистрируем пользователя (добавляем в базу данных)
                } else if (Objects.equals(messageText, "Вы хотите отписаться от рассылки уведомлений?")) {
                    executeEditMessageText(UNSUBSCRIBE_TRUE_TEXT, chatId, messageId);
                    deleteUserData(update.getCallbackQuery().getMessage());
                }
            } else if (callbackData.equals(NO_BUTTON)) { // Если была нажата кнопка "Нет"
                if (Objects.equals(messageText, "Вы хотите подписаться на рассылку уведомлений?")) {
                    executeEditMessageText(SUBSCRIBE_FALSE_TEXT, chatId, messageId); // Изменяем текст сообщения на подтверждение отказа от подписки
                } else if (Objects.equals(messageText, "Вы хотите отписаться от рассылки уведомлений?")) {
                    executeEditMessageText(UNSUBSCRIBE_FALSE_TEXT, chatId, messageId);
                }
            }
        }
    }

    private void registerUser(Message msg) {

        if (userRepository.findById(msg.getChatId()).isEmpty()) { // Проверяем, не зарегистрирован ли уже пользователь с таким chatId

            // Если пользователь с таким chatId не найден, выполняем регистрацию
            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();

            // Заполняем данные пользователя
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            // Сохраняем пользователя в базе данных
            userRepository.save(user);
            log.info("User saved: " + user);
        }
    }

    private void register(long chatId) {

        // Создаем новое сообщение
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
        message.setText("Вы хотите подписаться на рассылку уведомлений?"); // Устанавливаем текст сообщения

        // Создаем встроенную клавиатуру
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
        message.setReplyMarkup(markupInline);
        // Отправляем сообщение
        executeMessage(message);

    }

    private void deleteUserData(Message msg) {

        Optional<User> optionalUser = userRepository.findById(msg.getChatId());
        if (optionalUser.isPresent()) {

            // Если пользователь с таким chatId найден, выполняем удаление пользователя из базы данных
            User user = optionalUser.get();

            // Удаляем пользователя из базы данных
            userRepository.delete(user);
            log.info("User deleted: " + user);
        } else {
            log.info("User not found for chatId: " + msg.getChatId());
        }
    }

    private void delete(long chatId) {
        // Создаем новое сообщение
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // Устанавливаем chatId
        message.setText("Вы хотите отписаться от рассылки уведомлений?"); // Устанавливаем текст сообщения

        // Создаем встроенную клавиатуру
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
        message.setReplyMarkup(markupInline);
        // Отправляем сообщение
        executeMessage(message);
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
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        // Создаем первую строку клавиатуры
        KeyboardRow row = new KeyboardRow();
        // Добавляем кнопки в первую строку
        row.add("weather");
        row.add("get random joke");
        // Добавляем первую строку в список строк клавиатуры
        keyboardRows.add(row);
        // Создаем вторую строку клавиатуры
        row = new KeyboardRow();
        // Добавляем кнопки во вторую строку
        row.add("register");
        row.add("check my data");
        row.add("delete my data");
        // Добавляем вторую строку в список строк клавиатуры
        keyboardRows.add(row);
        // Устанавливаем список строк клавиатуры в объект клавиатуры
        keyboardMarkup.setKeyboard(keyboardRows);
        // Устанавливаем настраиваемую клавиатуру в сообщение
        message.setReplyMarkup(keyboardMarkup);
        // Отправляем сообщение
        executeMessage(message);
    }

    private void prepareAndSendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage(); // Создаем новое сообщение
        // Устанавливаем chatId и текст сообщения
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message); // Вызываем метод для выполнения отправки сообщения
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

