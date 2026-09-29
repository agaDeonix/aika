package dev.aika.assistant.actions;

import java.util.Locale;

public final class CommandParser {
    private CommandParser() {}

    public static Command parse(String recognizedText, String foregroundPackage) {
        if (recognizedText == null) return null;
        String text = recognizedText.toLowerCase(Locale.forLanguageTag("ru"))
                .replace('ё', 'е')
                .replaceAll("[^а-я ]", " ")
                .trim()
                .replaceAll(" +", " ");

        return switch (text) {
            case "айка назад" -> Command.BACK;
            case "айка домой", "айка закрой" -> Command.HOME;
            case "айка дальше" -> isYouTube(foregroundPackage) ? Command.NEXT : null;
            case "айка стоп" -> isYouTube(foregroundPackage) ? Command.PAUSE : null;
            case "айка продолжи" -> isYouTube(foregroundPackage) ? Command.PLAY : null;
            default -> null;
        };
    }

    private static boolean isYouTube(String packageName) {
        return "com.google.android.youtube".equals(packageName);
    }
}
