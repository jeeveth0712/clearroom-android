package com.clearroom.app.testing

import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.Material
import com.clearroom.app.data.Room
import com.clearroom.app.data.RoomForm
import com.clearroom.app.data.RtTake
import com.clearroom.app.data.SeatMeasure
import com.clearroom.app.data.Surfaces

/** Test-data builders with sensible defaults: each test states only what it cares about. */
fun aRoom(
    id: String = "room-1",
    name: String = "Physics lab",
    lengthM: Double = 9.0,
    widthM: Double = 7.0,
    heightM: Double = 3.0,
    rows: Int = 5,
    cols: Int = 6,
    students: Int = 0,
) = Room(id, name, lengthM, widthM, heightM, rows, cols, Surfaces(Material.TILE, Material.CONCRETE, Material.CONCRETE), students)

fun aValidForm(name: String = "Physics lab") = RoomForm(
    name = name,
    lengthM = "9",
    widthM = "7",
    heightM = "3",
    rows = "5",
    cols = "6",
    students = "0",
)

fun flatBands(db: Double) = BandLevels(List(7) { db })

fun aMeasuredSeat(row: Int, col: Int) = SeatMeasure(
    row = row,
    col = col,
    noise = flatBands(-60.0),
    rtTakes = listOf(RtTake(List(7) { 1.0 })),
)
