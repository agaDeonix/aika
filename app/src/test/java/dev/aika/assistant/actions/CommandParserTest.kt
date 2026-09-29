package dev.aika.assistant.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    @Test
    fun `global commands work in any application`() {
        assertEquals(Command.BACK, CommandParser.parse("айка назад", "other.app"))
        assertEquals(Command.HOME, CommandParser.parse("айка домой", "other.app"))
        assertEquals(Command.HOME, CommandParser.parse("айка закрой", "other.app"))
    }

    @Test
    fun `youtube commands require official youtube package`() {
        assertEquals(
            Command.NEXT,
            CommandParser.parse("айка дальше", "com.google.android.youtube"),
        )
        assertNull(CommandParser.parse("айка дальше", "other.app"))
        assertNull(CommandParser.parse("айка стоп", "com.google.android.youtube.tv"))
    }

    @Test
    fun `activation word is mandatory and extra speech is rejected`() {
        assertNull(CommandParser.parse("дальше", "com.google.android.youtube"))
        assertNull(CommandParser.parse("айка пожалуйста дальше", "com.google.android.youtube"))
    }

    @Test
    fun `punctuation and letter yo are normalized`() {
        assertEquals(Command.BACK, CommandParser.parse("Айка, назад!", "other.app"))
    }
}
