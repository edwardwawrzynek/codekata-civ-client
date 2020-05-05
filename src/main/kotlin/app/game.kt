package app

import kotlin.math.abs

/**
 * A collection of resources
 */
data class ResourceAmounts(var food: Int, var production: Int, var trade: Int)

/**
 * Types of tiles
 */
enum class TileType (val apiIndex: Int) {
    Ocean(0),
    Grassland(1),
    Hills(2),
    Forest(3),
    Mountains(4),
    Fogged(-1)
}

/*
 * Types of harvested materials
 */
enum class ResourceType {
    Production,
    Trade,
    Food
}

// type of production
enum class ProductionType(val apiIndex: Int) {
    Army(0),
    Worker(1),
    City(2)
}

// type of research
enum class TechnologyType(val apiIndex: Int) {
    Offense(0),
    Defense(1)
}

/**
 * A map (just a tile configuration)
 */
data class GameMap(val size: Int, val contents: Array<Array<TileType>>) {

    // get the amount of resources that would be harvested on this tile, or null if the tile is fogged
    fun getHarvestAmounts(position: Pair<Int, Int>): ResourceAmounts {
        if(position.first < 0 || position.second < 0 || position.first >= size || position.second >= size) return ResourceAmounts(0, 0, 0)
        val type = contents[position.first][position.second]

        /* harvest table */
        return when(type) {
            TileType.Ocean -> ResourceAmounts(1, 0, 2)
            TileType.Grassland -> ResourceAmounts(2, 1, 0)
            TileType.Hills -> ResourceAmounts(2, 2, 1)
            TileType.Forest -> ResourceAmounts(2, 3, 0)
            TileType.Mountains -> ResourceAmounts(1, 1, 0)
            else -> ResourceAmounts(0, 0, 0)
        }
    }

    // get the combat multiplier that a tile provides, or null if that tile is fogged
    fun getCombatMultiplier(position: Pair<Int, Int>): Double? {
        val type = contents[position.first][position.second]

        return when(type) {
            TileType.Ocean -> 0.5
            TileType.Grassland -> 1.0
            TileType.Hills -> 1.5
            TileType.Forest -> 1.5
            TileType.Mountains -> 2.0
            else -> null
        }
    }
}

/**
 * Something owned by a player that has a place on the board (city, worker, army)
 */
open class BoardObject(var position: Pair<Int, Int>) {
    // calculate the manhattan distance to another object on the board
    fun distanceTo(other: BoardObject): Int {
        return abs(position.first - other.position.first) + abs(position.second - other.position.second)
    }
    fun distanceTo(other: Pair<Int, Int>): Int {
        return abs(position.first - other.first) + abs(position.second - other.second)
    }
}

// a city instance
class City(position: Pair<Int, Int>): BoardObject(position)

// A movable BoardObject (worker or army)
abstract class BoardUnit(position: Pair<Int, Int>): BoardObject(position) {
    // if a position is a valid move for this unit
    fun isValidMove(newPos: Pair<Int, Int>): Boolean {
        return distanceTo(newPos) <= 1
    }
}

// an army unit
class Army(position: Pair<Int, Int>, val hitpoints: Int): BoardUnit(position)

// a worker unit
class Worker(position: Pair<Int, Int>): BoardUnit(position)

/**
 * A player and is associated state
 **/
class Player(
        var offensiveStrength: Double,
        var defensiveStrength: Double,
        val cities: MutableList<City>,
        val workers: MutableList<Worker>,
        val armies: MutableList<Army>,
        val resources: ResourceAmounts
) {

}