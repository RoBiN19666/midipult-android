# MidiPult Android

Тонкая WebView-оболочка для панели MidiPult на Яндекс Станции Миди.
Интерфейс живёт на станции, поэтому изменения панели **не требуют
пересборки APK**.

## Возможности

- UDP-discovery панелей в локальной сети (порт 8056).
- Несколько станций, переключение и переименование.
- Первый запуск сам ищет колонки; если одна — открывает сразу.
- Ручной IP, если discovery блокирует роутер.
- WebView с PIN, cookies, скачиванием бэкапов и выбором файлов.
- Проверка и установка обновлений APK из GitHub Releases.
- Обновления APK и панели независимы.

## Сборка

Нужны Android SDK, JDK 17 и Gradle 8.11+.

```sh
gradle assembleDebug
```

Подписанный release:

```sh
export ANDROID_KEYSTORE_PATH=release.keystore
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...
gradle assembleRelease
```

`release.keystore` не коммитится.

## GitHub Actions

Workflow `.github/workflows/android.yml`:

- по тегу `android-v*` собирает APK;
- с secrets собирает подписанный release;
- без secrets на теге завершается ошибкой, чтобы не опубликовать debug;
- публикует `midipult-android-<version>.apk` и `.sha256`.

Secrets:

- `ANDROID_KEYSTORE_BASE64`;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`.

## Discovery

Станция отвечает на UDP-broadcast `MYPULT_DISCOVER` на порт **8056**.
Ответ: `service`, `api`, `name`, `model`, `version`, `ip`, `port`.
Секретов в ответе нет.

## Панель

Панель и её релизы: https://github.com/RoBiN19666/midipult
