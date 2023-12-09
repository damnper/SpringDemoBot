package io.proj3ct.SpringDemoBot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

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

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userName='" + userName + '\'' +
                ", registeredAt=" + registeredAt +
                '}';
    }
}
