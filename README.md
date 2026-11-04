<div align="center">

<img src="docs/img/logo.png" alt="MidiPult Android" width="520">

# MidiPult Android

**Пульт для Яндекс Станции Миди — в кармане.**

[![Release](https://img.shields.io/github/v/release/RoBiN19666/midipult-android?style=for-the-badge&label=release&color=7C5CFF)](https://github.com/RoBiN19666/midipult-android/releases)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/RoBiN19666/midipult-android)
[![WebView](https://img.shields.io/badge/WebView-тонкая_оболочка-22D3EE?style=for-the-badge)](https://github.com/RoBiN19666/midipult-android)

[Репозиторий приложения Android](https://github.com/RoBiN19666/midipult-android) ·
[Репозиторий панели](https://github.com/RoBiN19666/midipult) ·
[Скачать APK](https://github.com/RoBiN19666/midipult-android/releases/latest)

</div>

---

## Что это

Тонкая Android-оболочка вокруг веб-панели **MidiPult**. Приложение не содержит
интерфейс панели: оно находит станцию в локальной сети и открывает её WebView.
Поэтому изменения панели **не требуют пересборки APK**.

## Возможности

- автоматический поиск станций по UDP (порт `8056`);
- несколько станций, переключение и переименование;
- первый запуск сам находит колонку; если она одна — открывает сразу;
- ручной ввод IP, если discovery блокирует роутер;
- PIN, cookies и сессия панели;
- скачивание бэкапов и выбор файлов;
- проверка и установка обновлений APK из GitHub Releases;
- отдельные каналы обновлений приложения и панели.

## Скриншоты

| Настройка | Панель |
|---|---|
| ![Настройка](docs/img/setup.png) | ![Панель](docs/img/main.png) |

## Как подключить

1. Установите APK.
2. Подключите телефон и колонку к одной Wi-Fi сети.
3. Откройте приложение — оно найдёт станции автоматически.
4. Если станций несколько, выберите нужную.
5. Если поиск не работает, нажмите **«IP вручную»** и введите адрес панели.

## Обновления

APK проверяет релизы репозитория `midipult-android` не чаще раза в сутки,
скачивает файл `midipult-android-<версия>.apk`, проверяет SHA-256 и открывает
системный установщик. Тихая установка без подтверждения пользователя в
Android запрещена.

Панель обновляется отдельно, из своего репозитория, и APK при этом не
пересобирается.

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

### GitHub Actions

Workflow `.github/workflows/android.yml` собирает APK по тегу `android-v*`.
Для подписанного релиза нужны secrets:

- `ANDROID_KEYSTORE_BASE64`;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`.

Без секретов тег завершается ошибкой — debug-сборка не публикуется как
релизный APK.

## Ссылки

- [Репозиторий приложения Android](https://github.com/RoBiN19666/midipult-android)
- [Репозиторий панели](https://github.com/RoBiN19666/midipult)
- [Все релизы APK](https://github.com/RoBiN19666/midipult-android/releases)
- [Последний APK](https://github.com/RoBiN19666/midipult-android/releases/latest)
