<p align="center">
  <img src="https://i.ibb.co/N1myNTQ/tg-bot-apatrment-notify-service.png" alt="Project logo" width="200">
</p>

<p align="center">
   <img src="https://img.shields.io/badge/Version-v1.0%20(Alpha)-brown" alt="Version">
   <img src="https://img.shields.io/badge/JDK-21-orange" alt="JDK">
   <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-green" alt="Spring Boot">
   <img src="https://img.shields.io/badge/Telegrambot-6.8.0-blue" alt="Telegrambot">
   <img src="https://img.shields.io/badge/License-MIT-magenta" alt="License">
</p>

# RoamAndHuntBot

RoamAndHuntBot is a Telegram bot that keeps you informed about new apartment listings on CIAN, delivering real-time notifications directly to your chat.

## Features

- **Instant Updates**: Receive immediate notifications when new apartments are posted on [CIAN](https://kazan.cian.ru/).
- **Customized Alerts**: Subscribe to specific apartment types to tailor your notifications.
- **User-Friendly Commands**: Interact seamlessly with commands like /subscribe, /unsubscribe, /help, and more.

## Getting Started

1. **Subscribe**: Start your journey by subscribing to RoamAndHuntBot using the /subscribe command.
2. **Choose Apartment Types**: Select the apartment types you are interested in receiving notifications for.
3. **Stay Informed**: RoamAndHuntBot will keep you posted on the latest apartments that match your preferences.

## Commands

- `/subscribe`: Begin receiving notifications.
- `/unsubscribe`: Stop receiving notifications.
- `/addtype`: Add an apartment type to your preferences.
- `/removetype`: Remove an apartment type from your preferences.
- `/help`: Learn more about available commands.

### Additional Configuration

To connect to another city on CIAN, set the link to your city with the "Sort: new first" filter and select all apartment types by updating the `LINK_CONNECT_TO_CIAN` static variable.


## Developers

- [Yunusov Daler](https://github.com/damnper)

## Contributing

Feel free to contribute to the project by submitting bug reports, feature requests, or pull requests. Your feedback is highly appreciated.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
