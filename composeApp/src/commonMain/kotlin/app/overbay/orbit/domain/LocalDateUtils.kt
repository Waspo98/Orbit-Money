package app.overbay.orbit.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

object LocalDateUtils {

    fun todayLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate {
        return Clock.System.todayIn(timeZone)
    }

    fun todayIsoDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        return formatIsoDate(todayLocalDate(timeZone))
    }

    fun currentYearMonth(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        return formatYearMonth(todayLocalDate(timeZone))
    }

    fun parseIsoDate(value: String?): LocalDate? {
        if (value.isNullOrBlank() || value.length < 10) return null
        return try {
            LocalDate.parse(value.substring(0, 10))
        } catch (_: Exception) {
            null
        }
    }

    fun parseYearMonth(value: String?): Pair<Int, Int>? {
        if (value.isNullOrBlank() || value.length < 7) return null
        val parts = value.split("-")
        if (parts.size < 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return Pair(year, month)
    }

    fun formatIsoDate(date: LocalDate): String {
        val y = date.year.toString().padStart(4, '0')
        val m = date.monthNumber.toString().padStart(2, '0')
        val d = date.dayOfMonth.toString().padStart(2, '0')
        return "$y-$m-$d"
    }

    fun formatYearMonth(year: Int, monthNumber: Int): String {
        val y = year.toString().padStart(4, '0')
        val m = monthNumber.toString().padStart(2, '0')
        return "$y-$m"
    }

    fun formatYearMonth(date: LocalDate): String = formatYearMonth(date.year, date.monthNumber)

    fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun monthBounds(yearMonth: String): Pair<LocalDate, LocalDate> {
        val (year, month) = parseYearMonth(yearMonth) ?: run {
            val now = todayLocalDate()
            Pair(now.year, now.monthNumber)
        }
        val start = LocalDate(year, month, 1)
        val end = LocalDate(year, month, daysInMonth(year, month))
        return Pair(start, end)
    }

    fun addMonthsToYearMonth(yearMonth: String, monthsToAdd: Int): String {
        val (year, month) = parseYearMonth(yearMonth) ?: return yearMonth
        val totalMonths = (year * 12) + (month - 1) + monthsToAdd
        val nextYear = totalMonths / 12
        val nextMonth = (totalMonths % 12) + 1
        return formatYearMonth(nextYear, nextMonth)
    }

    fun addMonthsClamped(date: LocalDate, monthsToAdd: Int): LocalDate {
        val totalMonths = (date.year * 12) + (date.monthNumber - 1) + monthsToAdd
        val nextYear = totalMonths / 12
        val nextMonth = (totalMonths % 12) + 1
        val maxDays = daysInMonth(nextYear, nextMonth)
        val nextDay = date.dayOfMonth.coerceAtMost(maxDays)
        return LocalDate(nextYear, nextMonth, nextDay)
    }

    fun addDays(date: LocalDate, days: Int): LocalDate = date.plus(days, DateTimeUnit.DAY)

    fun daysBetween(startDate: LocalDate, endDate: LocalDate): Int {
        return startDate.daysUntil(endDate)
    }

    fun daysBetween(startIso: String, endIso: String): Int? {
        val s = parseIsoDate(startIso) ?: return null
        val e = parseIsoDate(endIso) ?: return null
        return daysBetween(s, e)
    }

    fun isToday(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        return date == todayLocalDate(timeZone)
    }

    fun isYesterday(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        return date == todayLocalDate(timeZone).minus(1, DateTimeUnit.DAY)
    }

    fun isThisMonth(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = todayLocalDate(timeZone)
        return date.year == today.year && date.monthNumber == today.monthNumber
    }

    fun monthName(monthNumber: Int): String {
        return when (monthNumber) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> ""
        }
    }

    fun monthShortName(monthNumber: Int): String {
        return monthName(monthNumber).take(3)
    }

    fun formatMonthLabel(yearMonth: String): String {
        val (year, month) = parseYearMonth(yearMonth) ?: return yearMonth
        return "${monthName(month)} $year"
    }

    fun formatMonthShortLabel(yearMonth: String): String {
        val (year, month) = parseYearMonth(yearMonth) ?: return yearMonth
        return "${monthShortName(month)} $year"
    }

    fun formatDisplayDate(date: LocalDate): String {
        return "${monthShortName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"
    }

    fun formatShortDate(date: LocalDate): String {
        return "${monthShortName(date.monthNumber)} ${date.dayOfMonth}"
    }

    fun formatRelativeOrDisplayDate(date: LocalDate): String {
        return when {
            isToday(date) -> "Today"
            isYesterday(date) -> "Yesterday"
            else -> formatDisplayDate(date)
        }
    }

    fun ageFromBirthDate(birthDate: LocalDate, asOf: LocalDate = todayLocalDate()): Int {
        var age = asOf.year - birthDate.year
        if (asOf.monthNumber < birthDate.monthNumber ||
            (asOf.monthNumber == birthDate.monthNumber && asOf.dayOfMonth < birthDate.dayOfMonth)
        ) {
            age--
        }
        return age.coerceAtLeast(0)
    }
}
