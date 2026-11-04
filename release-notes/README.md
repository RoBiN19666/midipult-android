# Release notes

Перед выпуском новой версии Android добавьте файл `<versionName>.md`, например `1.0.8.md`.

Workflow `.github/workflows/android.yml` при пуше тега `android-v<versionName>`:
- читает `version.properties`;
- проверяет, что тег совпадает с `versionName`;
- требует наличие `release-notes/<versionName>.md`;
- подставляет этот файл в тело GitHub Release.

Без файла заметок релиз не публикуется. Это защищает от одинаковых бессодержательных описаний.
