package com.example.personalovertimerecord.utils

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

object InputValidator {
    
    fun validateOvertimeHours(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Success
        }
        
        val hours = value.toDoubleOrNull()
        return when {
            hours == null -> ValidationResult.Error("请输入有效的数字")
            hours < 0 -> ValidationResult.Error("加班时长不能为负数")
            hours > 24 -> ValidationResult.Error("加班时长不能超过24小时")
            else -> ValidationResult.Success
        }
    }
    
    fun validateExtraHours(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Success
        }
        
        val hours = value.toDoubleOrNull()
        return when {
            hours == null -> ValidationResult.Error("请输入有效的数字")
            hours < 0 -> ValidationResult.Error("加点时长不能为负数")
            hours > 24 -> ValidationResult.Error("加点时长不能超过24小时")
            else -> ValidationResult.Success
        }
    }
    
    fun validateBaseSalary(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Error("请输入基本工资")
        }
        
        val salary = value.toDoubleOrNull()
        return when {
            salary == null -> ValidationResult.Error("请输入有效的数字")
            salary < 0 -> ValidationResult.Error("基本工资不能为负数")
            salary > 1000000 -> ValidationResult.Error("基本工资不能超过100万")
            else -> ValidationResult.Success
        }
    }
    
    fun validatePerformancePercent(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Success
        }
        
        val percent = value.toDoubleOrNull()
        return when {
            percent == null -> ValidationResult.Error("请输入有效的数字")
            percent < 0 -> ValidationResult.Error("绩效百分比不能为负数")
            percent > 500 -> ValidationResult.Error("绩效百分比不能超过500%")
            else -> ValidationResult.Success
        }
    }
    
    fun validateMonthlyWorkDays(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Error("请输入月工作日天数")
        }
        
        val days = value.toDoubleOrNull()
        return when {
            days == null -> ValidationResult.Error("请输入有效的数字")
            days < 1 -> ValidationResult.Error("月工作日天数不能小于1天")
            days > 31 -> ValidationResult.Error("月工作日天数不能超过31天")
            else -> ValidationResult.Success
        }
    }
    
    fun validateDailyWorkHours(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Error("请输入每日工作时长")
        }
        
        val hours = value.toDoubleOrNull()
        return when {
            hours == null -> ValidationResult.Error("请输入有效的数字")
            hours < 1 -> ValidationResult.Error("每日工作时长不能小于1小时")
            hours > 24 -> ValidationResult.Error("每日工作时长不能超过24小时")
            else -> ValidationResult.Success
        }
    }
    
    fun validateNote(value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Success
        }
        
        return if (value.length > 500) {
            ValidationResult.Error("备注长度不能超过500字符")
        } else {
            ValidationResult.Success
        }
    }
    
    fun parseOvertimeHours(value: String?): Double? {
        if (value.isNullOrBlank()) {
            return null
        }
        
        val hours = value.toDoubleOrNull()
        return if (hours != null && hours >= 0) {
            hours
        } else {
            null
        }
    }
    
    fun parseExtraHours(value: String?): Double? {
        if (value.isNullOrBlank()) {
            return null
        }
        
        val hours = value.toDoubleOrNull()
        return if (hours != null && hours >= 0) {
            hours
        } else {
            null
        }
    }
    
    fun parseDouble(value: String?): Double? {
        return value?.toDoubleOrNull()
    }
    
    fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.getDefault(), "%.2f", value)
        }
    }
}
