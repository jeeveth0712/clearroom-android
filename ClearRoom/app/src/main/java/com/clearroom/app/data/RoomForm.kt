package com.clearroom.app.data

/**
 * Accepted ranges for the Room form. The doc's seat layout needs length ≥ 3.3 m (seats run from
 * 2.5 m to length − 0.8 m) and width ≥ 1.6 m (0.8 m side margins); the upper limits catch typos
 * like 600 for 6.0 while still fitting small halls.
 */
object RoomLimits {
    val LENGTH_M = 3.5..60.0
    val WIDTH_M = 2.0..60.0
    val HEIGHT_M = 2.0..20.0
    val ROWS = 1..30
    val COLS = 1..30
    val STUDENTS = 0..500
    const val NAME_MAX_CHARS = 60
}

enum class RoomField { NAME, LENGTH, WIDTH, HEIGHT, ROWS, COLS, STUDENTS }

enum class FieldProblem { MISSING, NOT_A_NUMBER, OUT_OF_RANGE }

/** The Room form as typed: text fields stay text until Save, so half-typed input like "7." is fine. */
data class RoomForm(
    val name: String = "",
    val lengthM: String = "",
    val widthM: String = "",
    val heightM: String = "",
    val rows: String = "",
    val cols: String = "",
    val floor: Material = Material.TILE,
    val walls: Material = Material.CONCRETE,
    val ceiling: Material = Material.CONCRETE,
    val students: String = "0",
) {
    sealed interface Result {
        data class Valid(val room: Room) : Result
        data class Invalid(val problems: Map<RoomField, FieldProblem>) : Result
    }

    /** Validates every field and builds a [Room] with [id] when all are fine. */
    fun toRoom(id: String): Result {
        val problems = mutableMapOf<RoomField, FieldProblem>()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) problems[RoomField.NAME] = FieldProblem.MISSING
        if (trimmedName.length > RoomLimits.NAME_MAX_CHARS) problems[RoomField.NAME] = FieldProblem.OUT_OF_RANGE

        val length = decimal(RoomField.LENGTH, lengthM, RoomLimits.LENGTH_M, problems)
        val width = decimal(RoomField.WIDTH, widthM, RoomLimits.WIDTH_M, problems)
        val height = decimal(RoomField.HEIGHT, heightM, RoomLimits.HEIGHT_M, problems)
        val rowCount = whole(RoomField.ROWS, rows, RoomLimits.ROWS, problems)
        val colCount = whole(RoomField.COLS, cols, RoomLimits.COLS, problems)
        val studentCount = whole(RoomField.STUDENTS, students, RoomLimits.STUDENTS, problems)

        if (problems.isNotEmpty()) return Result.Invalid(problems)
        return Result.Valid(
            Room(
                id = id,
                name = trimmedName,
                lengthM = requireNotNull(length),
                widthM = requireNotNull(width),
                heightM = requireNotNull(height),
                rows = requireNotNull(rowCount),
                cols = requireNotNull(colCount),
                surfaces = Surfaces(floor, walls, ceiling),
                students = requireNotNull(studentCount),
            ),
        )
    }

    companion object {
        fun from(room: Room) = RoomForm(
            name = room.name,
            lengthM = room.lengthM.toPlainString(),
            widthM = room.widthM.toPlainString(),
            heightM = room.heightM.toPlainString(),
            rows = room.rows.toString(),
            cols = room.cols.toString(),
            floor = room.surfaces.floor,
            walls = room.surfaces.walls,
            ceiling = room.surfaces.ceiling,
            students = room.students.toString(),
        )

        /** "9.0" reads as "9" in the form; other values keep their decimals. */
        private fun Double.toPlainString(): String =
            if (this % 1.0 == 0.0) toLong().toString() else toString()

        /** Accepts "7.5" and "7,5": many phones offer a comma on the decimal keyboard. */
        private fun decimal(
            field: RoomField,
            text: String,
            range: ClosedFloatingPointRange<Double>,
            problems: MutableMap<RoomField, FieldProblem>,
        ): Double? {
            val trimmed = text.trim().replace(',', '.')
            if (trimmed.isEmpty()) return null.also { problems[field] = FieldProblem.MISSING }
            val value = trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return null.also { problems[field] = FieldProblem.NOT_A_NUMBER }
            if (value !in range) problems[field] = FieldProblem.OUT_OF_RANGE
            return value
        }

        private fun whole(
            field: RoomField,
            text: String,
            range: IntRange,
            problems: MutableMap<RoomField, FieldProblem>,
        ): Int? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null.also { problems[field] = FieldProblem.MISSING }
            val value = trimmed.toIntOrNull() ?: return null.also { problems[field] = FieldProblem.NOT_A_NUMBER }
            if (value !in range) problems[field] = FieldProblem.OUT_OF_RANGE
            return value
        }
    }
}
