package tasklist

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.datetime.*
import kotlinx.datetime.Clock.*
import java.io.File
import java.util.Random
import java.util.UUID


fun main() {
    val allowedCommands = AllowedCommands.values().map { it.name.lowercase() }
    val taskDescriptions = TaskPersister.readTasks() ?: mutableListOf()

    while (true) {
        println("Input an action (${allowedCommands.joinToString()}):")
        val rawInput = readln()
        if (!allowedCommands.contains(rawInput)) {
            println("The input action is invalid")
            continue
        }

        when (AllowedCommands.valueOf(rawInput.uppercase())) {
            AllowedCommands.ADD -> taskDescriptions.add(TaskDescription(Random().nextInt()))
            AllowedCommands.PRINT -> printAction(taskDescriptions)
            AllowedCommands.DELETE -> deleteAction(taskDescriptions)
            AllowedCommands.EDIT -> editAction(taskDescriptions)
            AllowedCommands.END -> {
                println("Tasklist exiting!")
                break
            }
        }
    }

    TaskPersister.writeTasks(taskDescriptions)
}

object TaskPersister {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val jsonFile = File("tasklist.json")
    private val type =
        Types.newParameterizedType(MutableList::class.java, TaskDescription::class.java, UUID::class.java)
    private val adapter = moshi.adapter<MutableList<TaskDescription>>(type)

    fun readTasks(): MutableList<TaskDescription>? {
        return try {
            val rawTasks = jsonFile.readText()
            adapter.fromJson(rawTasks)
        } catch (e: Exception) {
            null
        }
    }

    fun writeTasks(taskDescriptions: MutableList<TaskDescription>) {
        jsonFile.writeText(adapter.toJson(taskDescriptions))
    }
}

fun editAction(taskDescriptions: MutableList<TaskDescription>) {
    if (taskDescriptions.isEmpty()) {
        println("No tasks have been input")
        return
    }

    printAction(taskDescriptions)

    val taskNumber: Int = readCorrectTaskNumber(taskDescriptions)
    while (true) {
        println("Input a field to edit (priority, date, time, task):")
        val field = readln()
        val task = taskDescriptions[taskNumber - 1]
        val result = task.tryEditField(field)
        if (!result.success) {
            println(result.reason)
        } else {
            println("The task is changed")
            break
        }
    }
}

fun deleteAction(taskDescriptions: MutableList<TaskDescription>) {
    if (taskDescriptions.isEmpty()) {
        println("No tasks have been input")
        return
    }

    printAction(taskDescriptions)

    val taskNumber: Int = readCorrectTaskNumber(taskDescriptions)
    taskDescriptions.removeAt(taskNumber - 1)
    println("The task is deleted")
}

fun printAction(taskDescriptions: MutableList<TaskDescription>) {
    if (taskDescriptions.isEmpty()) {
        println("No tasks have been input")
        return
    }

    TaskDescriptionPrinter.printFormattedTasks(taskDescriptions)
}

private fun readCorrectTaskNumber(taskDescriptions: MutableList<TaskDescription>): Int {
    var taskNumber: Int?
    while (true) {
        println("Input the task number (1-${taskDescriptions.size}):")
        taskNumber = readln().toIntOrNull()
        if (taskNumber == null || taskNumber !in 1..taskDescriptions.size) {
            println("Invalid task number")
        } else {
            return taskNumber
        }
    }
}

data class TaskDescription(
    var id: Int,
    var priority: Priority,
    var date: String,
    var time: Time,
    var task: MutableList<String>
) {
    val dueTag
        get() = calculateDueTag()
    private val fieldNames = arrayOf("priority", "date", "time", "task")

    constructor(id: Int) : this(id, Priority.C, "", Time("0", "0"), mutableListOf()) {
        priority = setPriority()
        date = setDate()
        time = setTime()
        task = setDescription()
    }

    fun getDate(): LocalDate {
        val dateParts = date.split(',')
        return LocalDate(dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt())
    }

    private fun calculateDueTag(): DueTag {
        val currentDate = System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
        val numberOfDays = currentDate.daysUntil(getDate())

        return when {
            numberOfDays == 0 -> DueTag.T
            numberOfDays > 0 -> DueTag.I
            else -> DueTag.O
        }
    }

    private fun setDescription(): MutableList<String> {
        println("Input a new task (enter a blank line to end):")

        val taskDescription = mutableListOf<String>()
        var rawInput = readln()
        while (rawInput.isNotBlank()) {
            taskDescription.add(rawInput.trim())

            rawInput = readln()
        }

        if (taskDescription.isEmpty()) {
            println("The task is blank")
        }

        return taskDescription
    }

    private fun setPriority(): Priority {
        while (true) {
            println("Input the task priority (C, H, N, L):")

            val input = readln().uppercase()
            if (input in Priority.values().map { it.name }) {
                return Priority.valueOf(input)
            }
        }
    }

    private fun setDate(): String {
        while (true) {
            println("Input the date (yyyy-mm-dd):")

            try {
                val dateParts = readln().split("-").map { it.toInt() }
                dateParts.let { LocalDate(it[0], it[1], it[2]) }

                return dateParts.let { listOf(it[0], it[1], it[2]).joinToString(",") }
            } catch (e: Exception) {
                println("The input date is invalid")
            }
        }
    }

    private fun setTime(): Time {
        while (true) {
            println("Input the time (hh:mm):")
            val time = readln()
            val parts = time.split(":").toMutableList()
            if (parts.size != 2) {
                println("The input time is invalid")
                continue
            }

            try {
                return Time(parts.first(), parts.last())
            } catch (e: Exception) {
                println("The input time is invalid")
            }
        }
    }

    fun tryEditField(field: String): Result {
        if (!fieldNames.contains(field)) {
            return Result.fail("Invalid field")
        }

        when (field) {
            "priority" -> this.priority = setPriority()
            "date" -> this.date = setDate()
            "time" -> this.time = setTime()
            "task" -> this.task = setDescription()
        }

        return Result.success()
    }
}

class Time(hours: String, minutes: String) {
    private var hours = hours
        set(value) {
            val numberOfHours = value.toIntOrNull()
            if (numberOfHours == null || numberOfHours < 0 || numberOfHours > 23) throw IllegalArgumentException()
            field = if (value.length == 1) "0$value" else value
        }
    private var minutes = minutes
        set(value) {
            val numberOfMinutes = value.toIntOrNull()
            if (numberOfMinutes == null || numberOfMinutes < 0 || numberOfMinutes > 59) throw IllegalArgumentException()
            field = if (value.length == 1) "0$value" else value
        }

    init {
        this.hours = hours
        this.minutes = minutes
    }

    override fun toString(): String {
        return "$hours:$minutes"
    }
}

enum class AllowedCommands {
    ADD,
    PRINT,
    EDIT,
    DELETE,
    END,
}

enum class Priority {
    C,
    H,
    N,
    L,
}

enum class DueTag {
    I,
    T,
    O
}

class Result private constructor(var success: Boolean, val reason: String? = null) {
    companion object {
        fun success(): Result {
            return Result(true)
        }

        fun fail(reason: String): Result {
            return Result(false, reason)
        }
    }
}

object TaskDescriptionPrinter {
    private const val numberColumnSpaces = 4
    private const val dateColumnSpaces = 12
    private const val timeColumnSpaces = 7
    private const val priorityColumnSpaces = 3
    private const val dueToColumnSpaces = 3
    private const val taskDescriptionColumnSpaces = 44
    private const val columDelimiter = '|'
    private val colorPattern = { number: Int -> "\u001B[10${number}m \u001B[0m" }

    private val priorityColorsMapping = mapOf(
        Priority.C to colorPattern(1),
        Priority.H to colorPattern(3),
        Priority.N to colorPattern(2),
        Priority.L to colorPattern(4),
    )

    private val dueToColorMapping = mapOf(
        DueTag.I to colorPattern(2),
        DueTag.T to colorPattern(3),
        DueTag.O to colorPattern(1),
    )

    fun printFormattedTasks(tasks: MutableList<TaskDescription>) {
        printRowDelimiter()
        printHeader()
        printRowDelimiter()

        val spacesCount = 3
        for ((i, task) in tasks.withIndex()) {
            val orderNumberLength = (i + 1).toString().length
            val firstLineSpaces = spacesCount - orderNumberLength

            for ((j, line) in task.task.withIndex()) {
                if (j != 0) {
                    printEmptyPrefix()
                } else {
                    print(
                        "$columDelimiter ${(i + 1)}${" ".padEnd(firstLineSpaces)}" +
                                "$columDelimiter ${task.getDate()} " +
                                "$columDelimiter ${task.time} " +
                                "$columDelimiter ${priorityColorsMapping[task.priority]} " +
                                "$columDelimiter ${dueToColorMapping[task.dueTag]} "
                    )
                }
                for ((k, chunk) in line.chunked(taskDescriptionColumnSpaces).withIndex()) {
                    if (k != 0) {
                        printEmptyPrefix()
                    }
                    print("${columDelimiter}${chunk.padEnd(taskDescriptionColumnSpaces)}")
                    println(columDelimiter)
                }
            }

            printRowDelimiter()
        }
    }

    private fun printEmptyPrefix() {
        print(
            "$columDelimiter${" ".padEnd(numberColumnSpaces)}" +
                    "$columDelimiter${" ".padEnd(dateColumnSpaces)}" +
                    "$columDelimiter${" ".padEnd(timeColumnSpaces)}" +
                    "$columDelimiter${" ".padEnd(priorityColumnSpaces)}" +
                    "$columDelimiter${" ".padEnd(dueToColumnSpaces)}"
        )
    }

    private fun printRowDelimiter() {
        println(
            "+${"-".repeat(numberColumnSpaces)}" +
                    "+${"-".repeat(dateColumnSpaces)}" +
                    "+${"-".repeat(timeColumnSpaces)}" +
                    "+${"-".repeat(priorityColumnSpaces)}" +
                    "+${"-".repeat(dueToColumnSpaces)}" +
                    "+${"-".repeat(taskDescriptionColumnSpaces)}" +
                    "+"
        )
    }

    private fun printHeader() {
        val indentLength = (taskDescriptionColumnSpaces - "Task".length) / 2
        println(
            "$columDelimiter N  " +
                    "$columDelimiter    Date    " +
                    "$columDelimiter Time  " +
                    "$columDelimiter P " +
                    "$columDelimiter D " +
                    "$columDelimiter${" ".repeat(indentLength - 1)}Task${" ".repeat(indentLength + 1)}" +
                    "$columDelimiter"
        )
    }
}