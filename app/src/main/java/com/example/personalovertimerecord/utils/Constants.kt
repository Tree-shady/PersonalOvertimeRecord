package com.example.personalovertimerecord.utils

object Constants {
    
    const val TIME_UPDATE_INTERVAL = 1000L
    
    const val SPLASH_DURATION = 2000L
    
    const val MAX_DAILY_OVERTIME_HOURS = 24.0
    const val MIN_OVERTIME_HOURS = 0.0
    
    const val MAX_MONTHLY_WORK_DAYS = 31
    const val MIN_MONTHLY_WORK_DAYS = 1
    const val DEFAULT_MONTHLY_WORK_DAYS = 21.75
    
    const val MAX_DAILY_WORK_HOURS = 24.0
    const val MIN_DAILY_WORK_HOURS = 0.1
    const val DEFAULT_DAILY_WORK_HOURS = 8.0
    
    const val MIN_OVERTIME_RATE = 1.0
    const val MAX_PERFORMANCE_PERCENT = 500.0
    const val DEFAULT_PERFORMANCE_PERCENT = 0.0
    
    const val DEFAULT_OVERTIME_RATE_NORMAL = 1.5
    const val DEFAULT_OVERTIME_RATE_WEEKEND = 2.0
    const val DEFAULT_OVERTIME_RATE_HOLIDAY = 3.0
    
    const val DEFAULT_BASE_SALARY = 5000.0
    const val MIN_BASE_SALARY = 0.0
    const val MAX_BASE_SALARY = 1000000.0
    
    const val DEFAULT_WORK_START_TIME = "08:00"
    const val DEFAULT_WORK_END_TIME = "17:00"
    
    const val NOTE_MAX_LENGTH = 500
    
    const val OVERTIME_NOT_SET = -1.0
}
