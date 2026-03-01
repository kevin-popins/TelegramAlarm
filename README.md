\# TelegramAlarm



!\[Android CI](https://github.com/kevin-popins/TelegramAlarm/actions/workflows/android-ci.yml/badge.svg)



RU  

TelegramAlarm — лёгкое Android-приложение, которое превращает входящие уведомления (в первую очередь Telegram) в «будильник» для выбранных рабочих чатов. Приложение не использует Telegram API и не является клиентом Telegram. Оно работает на уровне системных уведомлений Android через Notification Listener Service.



EN  

TelegramAlarm is a lightweight Android utility that turns incoming notifications (primarily Telegram) into a noticeable “alarm” for selected work chats. It is not a Telegram client and does not use the Telegram API. It works by reading Android system notifications via Notification Listener Service.



\## Screenshots



| | | | |

|---|---|---|---|

| !\[s1](docs/screenshots/01.png) | !\[s2](docs/screenshots/02.png) | !\[s3](docs/screenshots/03.png) | !\[s4](docs/screenshots/04.png) |



\## What it does



RU  

Приложение слушает системные уведомления после того, как пользователь вручную включает доступ Notification Listener в настройках Android. При совпадении с заданными названиями рабочих чатов запускается сигнал через foreground service, что повышает стабильность работы в фоне. На вкладке Settings задаются названия чатов через “;”, выбирается звук, задаётся громкость и включается режим отслеживания.



EN  

The app listens to Android system notifications after the user manually grants Notification Listener access in system settings. When a notification matches your configured work chat names, it triggers an alarm via a foreground service for more reliable background operation. The Settings tab lets you configure chat names separated by “;”, pick a sound, set volume, and enable tracking.



\## Privacy



RU  

Приложение не имеет доступа к переписке Telegram и не подключается к аккаунту. Используется только информация, которую Android уже показывает в уведомлениях.



EN  

The app does not access Telegram chat history and does not connect to your account. It uses only what Android already exposes via notifications.

