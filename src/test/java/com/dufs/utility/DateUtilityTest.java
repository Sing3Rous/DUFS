package com.dufs.utility;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilityTest {

    @Test
    void dateToShort() {
        final LocalDate date = LocalDate.of(2022, 3, 29);
        final short encodedDate = DateUtility.dateToShort(date);
        assertEquals(16002, encodedDate);
    }

    @Test
    void shortToDate() {
        final short encodedDate = 16002;
        final int[] date = DateUtility.shortToDate(encodedDate);
        assertEquals(2022, date[0]);
        assertEquals(3, date[1]);
        assertEquals(29, date[2]);
    }

    @Test
    void timeToShort() {
        final LocalDateTime time = LocalDateTime.of(2022, 3, 29, 12, 13, 14);
        final short encodedTime = DateUtility.timeToShort(time);
        assertEquals(24999, encodedTime);
    }

    @Test
    void shortToTime() {
        final short encodedTime = 24999;
        final int[] time = DateUtility.shortToTime(encodedTime);
        assertEquals(12, time[0]);
        assertEquals(13, time[1]);
        assertEquals(14, time[2]);
    }
}