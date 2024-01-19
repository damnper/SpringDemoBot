package io.proj3ct.SpringDemoBot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

// Эта аннотация помечает класс как сущность JPA (Java Persistence API),
// представляющую объект, который может быть сохранен в базе данных.
// Переменная 'name' указывает имя таблицы базы данных для этой сущности.
@Getter
@Setter
@Entity(name = "usersDataTable")
public class User {
    @Id
    private Long chatId;
    private String firstName;
    private String lastName;
    private String userName;
    private Timestamp registeredAt;
    private String apartmentType;
    private String apartmentDescription = "Отсутствует";
    private String apartmentLink = "Отсутствует";

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userName='" + userName + '\'' +
                ", registeredAt='" + registeredAt + '\'' +
                ", apartmentDescription='" + apartmentDescription + '\'' +
                ", apartmentLink='" + apartmentLink + '\'' +
                ", apartmentType=" + apartmentType +
                '}';
    }

    public String getUserDataDB() {
        LocalDate localDate = registeredAt.toLocalDateTime().toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String formattedDate = localDate.format(formatter);

        return "\n\uD83D\uDCC5 *Дата регистрации:* " + formattedDate + "\n\n" +
                "\uD83C\uDFE0 *Выбранный тип квартиры:* " + sortValues(apartmentType)  + "\n\n" +
                "\uD83D\uDCDD *Описание последней квартиры:* " + apartmentDescription  + "\n\n" +
                "\uD83D\uDD17 *Ссылка на последнюю квартиру:* " + apartmentLink;
    }

    private static String sortValues(String input) {
        String[] values = input.split(",");
        Arrays.sort(values);
        return String.join(",", values);
    }
}
