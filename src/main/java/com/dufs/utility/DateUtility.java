package com.dufs.utility;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DateUtility {
    private static final int REFERENCE_YEAR = 2020;
    public static short dateToShort(LocalDate date) {
        final int year = date.getYear() - REFERENCE_YEAR;
        final int month = date.getMonthValue();
        final int day = date.getDayOfMonth();
        short encodedDate = 0;
        encodedDate |= month;   // put month value in the end (natural limit is 5 bits)
        encodedDate <<= 4;      // shift 4 bits left
        encodedDate |= day;     // put day value in the end (natural limit is 4 bits)
        encodedDate <<= 7;      // shift 7 bits left
        encodedDate |= year;    // put year value in the end (naturally unlimited, but here is limited to 7 bits)
        return encodedDate;
    }

    public static int[] shortToDate(short encodedDate) {
        final int year = (encodedDate & 0b0000000001111111) + REFERENCE_YEAR;
        final int month = encodedDate & 0b1111100000000000;
        final int day = encodedDate & 0b0000011110000000;
        return new int[] { year, month, day };
    }

    public static short timeToShort(LocalDateTime time) {
        final int hour = time.getHour();
        final int minute = time.getMinute();
        final int doubleSecond = time.getSecond() / 2;
        short encodedTime = 0;
        encodedTime |= hour;            // put hour value in the end (natural limit is 5 bits)
        encodedTime <<= 6;              // shift 6 bits left
        encodedTime |= minute;          // put minute value in the end (natural limit is 6 bits)
        encodedTime <<= 5;              // shift 5 bits left
        encodedTime |= doubleSecond;    // put double second value in the end (natural limit is 5 bits)
        return encodedTime;
    }

    public static int[] shortToTime(short encodedTime) {
        final int hour = encodedTime & 0b1111100000000000;
        final int minute = encodedTime & 0b0000011111100000;
        final int doubleSecond = encodedTime & 0b0000000000011111;
        return new int[] { hour, minute, doubleSecond };
    }
}
