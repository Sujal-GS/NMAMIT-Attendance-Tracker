package com.nmamit.attendance.data.repository

import com.nmamit.attendance.data.model.DailyClassRecord
import com.nmamit.attendance.data.model.IAMarkItem
import com.nmamit.attendance.data.model.StudentInfo
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Offline self-contained demo dataset matching the web app's demo mode 1:1,
 * requiring zero external server connectivity.
 */
object DemoDataProvider {

    data class DemoSubjectSeed(
        val code: String,
        val name: String,
        val conducted: Int,
        val attended: Int
    )

    val demoSubjectsSeed: List<DemoSubjectSeed> = listOf(
        DemoSubjectSeed("21CS51", "Machine Learning & Neural Networks", 42, 39),
        DemoSubjectSeed("21CS52", "Database Management Systems", 38, 35),
        DemoSubjectSeed("21CS53", "Computer Networks & Protocols", 40, 32),
        DemoSubjectSeed("21CS54", "Design & Analysis of Algorithms", 44, 34),
        DemoSubjectSeed("21CS55", "Cloud Computing & DevOps", 36, 32),
        DemoSubjectSeed("21CSL56", "Machine Learning Laboratory", 14, 14),
        DemoSubjectSeed("21CSL57", "Database Applications Laboratory", 12, 11)
    )

    fun getDemoStudentInfo(regno: String): StudentInfo {
        val cleanRegno = regno.trim().take(30).ifBlank { "NNM24CS1234" }
        return StudentInfo(
            regno = cleanRegno,
            name = "Rohit Shenoy",
            degree = "B.Tech",
            branch = "Computer Science & Engineering",
            semester = "Semester 5 Examination 2026"
        )
    }

    fun getDemoAttendanceSummary(): List<SubjectAttendanceDto> {
        return demoSubjectsSeed.map { s ->
            SubjectAttendanceDto(
                subCode = s.code,
                subName = s.name,
                conducted = s.conducted.toString(),
                attended = s.attended.toString(),
                totalClass = s.conducted.toString(),
                presentClass = s.attended.toString()
            )
        }
    }

    fun getDemoDailyAttendance(dateStr: String): List<DailyClassRecord> {
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return emptyList()
        if (date.dayOfWeek == DayOfWeek.SUNDAY) return emptyList()

        if (date.dayOfWeek == DayOfWeek.SATURDAY) {
            val isPresent = date.dayOfMonth % 5 != 0
            return listOf(
                DailyClassRecord(
                    subCode = "21CSL56",
                    subName = "Machine Learning Laboratory",
                    period = "1-2",
                    noClass = "2",
                    present = if (isPresent) "1" else "0"
                )
            )
        }

        val schedule: List<Pair<String, DemoSubjectSeed>> = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> listOf(
                "1" to demoSubjectsSeed[0],
                "2" to demoSubjectsSeed[1],
                "3" to demoSubjectsSeed[2],
                "4" to demoSubjectsSeed[3]
            )
            DayOfWeek.TUESDAY -> listOf(
                "1" to demoSubjectsSeed[1],
                "2" to demoSubjectsSeed[2],
                "3" to demoSubjectsSeed[4],
                "4" to demoSubjectsSeed[0]
            )
            DayOfWeek.WEDNESDAY -> listOf(
                "1" to demoSubjectsSeed[3],
                "2" to demoSubjectsSeed[4],
                "3" to demoSubjectsSeed[0],
                "4" to demoSubjectsSeed[1],
                "5" to demoSubjectsSeed[2]
            )
            DayOfWeek.THURSDAY -> listOf(
                "1" to demoSubjectsSeed[0],
                "2" to demoSubjectsSeed[3],
                "3" to demoSubjectsSeed[2],
                "4" to demoSubjectsSeed[4]
            )
            DayOfWeek.FRIDAY -> listOf(
                "1" to demoSubjectsSeed[4],
                "2" to demoSubjectsSeed[1],
                "3" to demoSubjectsSeed[3],
                "4-5" to demoSubjectsSeed[6]
            )
            else -> emptyList()
        }

        return schedule.mapIndexed { idx, (period, sub) ->
            val seed = (date.dayOfMonth * 7 + idx * 13) % 17
            val isPresent = seed > 2
            DailyClassRecord(
                subCode = sub.code,
                subName = sub.name,
                period = period,
                noClass = if (period.contains("-")) "2" else "1",
                present = if (isPresent) "1" else "0"
            )
        }
    }

    fun getDemoIaMarks(): List<IAMarkItem> = listOf(
        IAMarkItem(
            slNo = "1",
            sem = "V",
            subcode = "CB3001-1",
            subname = "Information and Network Security",
            mse1 = "19.00",
            mse2 = "20.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "10.00",
            conduction = "10.00",
            mseLab = "25.00",
            finalMarks = "48.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "2",
            sem = "V",
            subcode = "CB2004-1",
            subname = "Principles of Database Management Systems",
            mse1 = "18.50",
            mse2 = "14.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "10.00",
            conduction = "10.00",
            mseLab = "30.00",
            finalMarks = "46.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "3",
            sem = "V",
            subcode = "CB3003-1",
            subname = "Essentials of Operating Systems",
            mse1 = "12.00",
            mse2 = "16.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "38.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "4",
            sem = "V",
            subcode = "CB3222-1",
            subname = "Malware Analysis and Detection",
            mse1 = "17.00",
            mse2 = "19.50",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "47.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "5",
            sem = "V",
            subcode = "HU1010-1",
            subname = "Research Methodology",
            mse1 = "16.00",
            mse2 = "18.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "44.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "6",
            sem = "V",
            subcode = "CB3603-1",
            subname = "Secure Full Stack Web Development",
            mse1 = "",
            mse2 = "",
            la1 = "",
            la2 = "",
            la3 = "",
            record = "9.00",
            conduction = "20.00",
            mseLab = "20.00",
            finalMarks = "49.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "7",
            sem = "V",
            subcode = "HU1006-1",
            subname = "Introduction to IPR",
            mse1 = "18.00",
            mse2 = "13.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "41.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "8",
            sem = "V",
            subcode = "HU1007-1",
            subname = "Social Connect & Responsibility",
            mse1 = "17.00",
            mse2 = "20.00",
            la1 = "4.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "47.00",
            eligibility = "Eligible"
        ),
        IAMarkItem(
            slNo = "9",
            sem = "V",
            subcode = "UM1003-1",
            subname = "Employability Skill Development",
            mse1 = "13.00",
            mse2 = "18.00",
            la1 = "2.00",
            la2 = "4.00",
            la3 = "2.00",
            record = "",
            conduction = "",
            mseLab = "",
            finalMarks = "39.00",
            eligibility = "Eligible"
        )
    )
}
