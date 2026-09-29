# Варианты реализации локального голосового ассистента на Android 16

Дата проверки: 2026-09-15. Целевое устройство: Lenovo Legion Y700 Gen 5, ZUI/Android 16.

## Краткий вывод

Обычный устанавливаемый APK может зарегистрировать `VoiceInteractionService`, быть выбран системным ассистентом и постоянно удерживаться системой. Более того, приложение с таким сервисом входит в исключения Android 14+, позволяющие запускать microphone foreground service из фона.

Роль ассистента, однако, не выдаёт обычному APK доступ к аппаратному DSP/SoundTrigger. Низкоэнергетический hotword-контур использует закрытые System API и разрешения для предустановленных/OEM-приложений. Поэтому переносимая архитектура для sideload APK — программный wake word через обычный микрофон. Она требует foreground service и системной индикации использования микрофона.

Иными словами, несовместимо не «приложение как ассистент», а сочетание всех трёх условий:

1. обычный пользовательский APK;
2. собственное постоянное голосовое слово-активатор;
3. полностью невидимое ожидание без системной индикации.

## Что даёт роль системного ассистента

- Публичный `VoiceInteractionService` доступен сторонним приложениям. Выбранный пользователем сервис система держит запущенным для быстрого вызова и фоновой инициализации: [Android API](https://developer.android.com/reference/android/service/voice/VoiceInteractionService).
- Приложение, предоставляющее `VoiceInteractionService`, является явным исключением из запрета запускать microphone foreground service из фона: [ограничения фонового запуска FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- Роль позволяет реализовать вызов жестом/долгим нажатием, `VoiceInteractionSession` и резервный ручной запуск.
- Роль не выдаёт `MANAGE_HOTWORD_DETECTION`, `CAPTURE_AUDIO_HOTWORD`, `MANAGE_SOUND_TRIGGER` и полный доступ к аппаратному SoundTrigger.

## Почему аппаратный hotword недоступен обычному APK

- Методы создания `AlwaysOnHotwordDetector`/`HotwordDetector` в актуальном framework помечены как `@SystemApi`/`@hide` и требуют `MANAGE_HOTWORD_DETECTION`: [AOSP VoiceInteractionService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/voice/VoiceInteractionService.java).
- В Android 16 `MANAGE_HOTWORD_DETECTION` имеет уровень `internal|preinstalled`; связанные разрешения также относятся к signature/privileged/role-контролю: [framework manifest](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/AndroidManifest.xml).
- Android 16 CDD прямо запрещает user-installable приложению предоставлять системный `HotwordDetectionService`: [Android 16 CDD](https://source.android.com/docs/compatibility/16/android-16-cdd).
- Reflection или компиляция против скрытых stubs не помогают: проверки разрешений выполняются в `system_server`.

## Как это делают существующие приложения

Открытые реализации подтверждают рабочую схему:

- [Hark](https://github.com/OpenAppCapabilityProtocol/hark) регистрируется как default assistant, но для `Hey Hark` использует локальный openWakeWord в background foreground service с постоянным уведомлением.
- [Dicio](https://github.com/DicioTeam/dicio-android) использует OpenWakeWord и Vosk; его wake-word часть также работает как foreground service.
- [Google Voice Access](https://support.google.com/accessibility/android/answer/6151848?hl=ru) требует тихое уведомление во время прослушивания и прямо сообщает о зелёном системном индикаторе микрофона на Android 12+.
- [Porcupine](https://github.com/Picovoice/porcupine) для долгоживущего Android wake-word режима предоставляет отдельный Service demo; это программный захват микрофона, а не доступ стороннего APK к OEM DSP.

Приложения Google, Lenovo, Samsung и другие предустановленные ассистенты находятся в другой категории: OEM может поместить пакет в `priv-app`, добавить его в allowlist разрешений и интегрировать с DSP/SoC. Правила такой поставки описаны в [AOSP privileged permission allowlist](https://source.android.com/docs/core/permissions/perms-allowlist).

## Реальные варианты

### 1. Default assistant + локальный microphone FGS — рекомендуемый

Состав:

- `VoiceInteractionService` и выбор приложения ассистентом;
- foreground service типа `microphone`;
- лёгкий локальный KWS для «Айка»;
- после wake word — локальное распознавание ограниченного словаря;
- `AccessibilityService` для свайпов/кликов/Back/Home;
- media key events для pause/play;
- остановка захвата при выключенном или заблокированном экране.

Плюсы: обычный debug APK, без root, локально, быстро, может восстановить прослушивание из фонового состояния. Минусы: зелёный индикатор микрофона и след foreground service в системе; возможна конкуренция с другими приложениями, использующими микрофон.

На Android 13+ пользователь может запретить `POST_NOTIFICATIONS`: FGS всё равно запускается, уведомление исчезает из обычной шторки, но приложение остаётся в системном Task Manager/«Активные приложения»: [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). Индикатор микрофона при обычном `AudioRecord` убрать штатно нельзя.

### 2. Существующий Google/Gemini hotword + наше приложение-исполнитель

Google/Gemini остаётся системным ассистентом и обрабатывает «Окей Google». Наше приложение экспортирует shortcut/deep link/App Action, после вызова выполняет жест через AccessibilityService. App Actions официально позволяют ассистенту запускать функции Android-приложения: [App Actions](https://developers.google.com/learn/pathways/app-actions).

Плюсы: нет нашего постоянного микрофона; используется OEM/Google hotword. Минусы: нельзя сохранить слово «Айка», возрастает зависимость от Google/GMS, сети, языка и конкретной реализации Gemini; управление другим приложением через такую цепочку выходит за основной сценарий App Actions и требует отдельной проверки на устройстве.

### 3. Вызов ассистента кнопкой/жестом, затем краткое слушание

Запуск через long-press Power/Home, Bluetooth-кнопку, accessibility shortcut или Quick Settings. Микрофон активируется только на время команды.

Плюсы: нет постоянного уведомления и постоянного индикатора, меньше расход батареи. Минус: это не полностью hands-free wake word.

### 4. Device Owner или Shizuku

Могут помочь с запуском компонентов или выполнением отдельных системных действий, но не выдают `internal`, `signature` и `privileged` hotword-разрешения. Поэтому проблему собственного невидимого wake word они не решают.

### 5. Root / system app / кастомная прошивка

Теоретически пакет можно встроить в системный образ как `priv-app`, добавить allowlist и использовать скрытые API. Но одного `root` или «systemize APK» недостаточно гарантированно: нужны правильный раздел, разрешения, SELinux, совместимый SoundTrigger HAL, модель ключевой фразы и иногда OEM/platform signing. Для Lenovo это отдельный рискованный R&D-трек, зависящий от прошивки.

## Рекомендация для проекта

Сначала сделать короткий технический прототип варианта 1 на реальном Lenovo:

1. зарегистрировать приложение как default assistant;
2. проверить непрерывный `AudioRecord` при YouTube на переднем плане, блокировке, перезапуске процесса и перезагрузке;
3. сравнить запуск KWS непосредственно из удерживаемого `VoiceInteractionService` и надёжный microphone FGS;
4. измерить видимость уведомления/Task Manager и поведение зелёного индикатора именно в ZUI;
5. только после подтверждения платформенного поведения подключать KWS, ASR и Accessibility-команды.

Наиболее вероятная итоговая архитектура: default assistant + microphone FGS + локальный KWS/ASR + AccessibilityService. Она реализует функциональный сценарий полностью; единственный неизбежный компромисс для обычного APK — системная индикация активного микрофона.
