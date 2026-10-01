package com.example.uir_android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormattingTest {
    @Test
    fun `group name is displayed instead of numeric id`() {
        assertEquals("Б22-501", formatAcademicGroup("б22  -  501"))
        assertEquals("М24-1001", formatAcademicGroup("М241001"))
        assertEquals("Не назначена", formatAcademicGroup(""))
    }

    @Test
    fun `profile score abbreviations are expanded`() {
        assertEquals("Посещение лекций", formatProfileScoreLabel("ПЛ"))
        assertEquals("Посещение семинаров", formatProfileScoreLabel("ПС"))
        assertEquals("Работа на семинарах", formatProfileScoreLabel("РС"))
        assertEquals("Контрольная работа №2", formatProfileScoreLabel("КР №2"))
        assertEquals("Итог за 1 раздел", formatProfileScoreLabel("Итог за 1 раздел"))
    }
}
