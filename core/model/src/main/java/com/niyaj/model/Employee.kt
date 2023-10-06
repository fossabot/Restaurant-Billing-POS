package com.niyaj.model

import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Employee(
    val employeeId: Int = 0,

    val employeeName: String = "",

    val employeePhone: String = "",

    val employeeSalary: String = "",

    val employeePosition: String = "",

    val employeeJoinedDate: String = "",

    val employeeEmail: String? = null,

    val employeeSalaryType: EmployeeSalaryType = EmployeeSalaryType.Daily,

    val employeeType: EmployeeType = EmployeeType.FullTime,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long? = null,
)

fun List<Employee>.searchEmployee(searchText: String): List<Employee> {
    return if (searchText.isNotEmpty()) {
        this.filter {
            it.employeeName.contains(searchText, true) ||
                    it.employeePosition.contains(searchText, true) ||
                    it.employeePhone.contains(searchText, true) ||
                    it.employeeSalaryType.name.contains(searchText, true) ||
                    it.employeeSalary.contains(searchText, true) ||
                    it.employeeJoinedDate.contains(searchText, true)
        }
    } else this
}

/**
 * Filter employee by search text
 * @param searchText String
 * @return Boolean
 */
fun Employee.filterEmployee(searchText: String): Boolean {
    return this.employeeName.contains(searchText, true) ||
            this.employeePosition.contains(searchText, true) ||
            this.employeePhone.contains(searchText, true) ||
            this.employeeSalaryType.name.contains(searchText, true) ||
            this.employeeSalary.contains(searchText, true) ||
            this.employeeJoinedDate.contains(searchText, true)
}