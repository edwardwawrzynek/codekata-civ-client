package app

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign
import kotlin.time.toDuration

// EDIT THIS FILE

class AI() {
    // Change the name that shows up on the graphical display
    // optional, but HIGHLY RECOMMENCED
    fun getName() = "Wawrzynek AI"

    // get the legal moves that a unit can make from a position
    fun legalMoves(position: Pair<Int, Int>, map: GameMap): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        if(position.first > 0) res.add(Pair(position.first - 1, position.second))
        if(position.second > 0) res.add(Pair(position.first, position.second - 1))
        if(position.first < map.size - 1) res.add(Pair(position.first + 1, position.second))
        if(position.second < map.size - 1) res.add(Pair(position.first, position.second + 1))

        return res
    }

    // get all tiles within manhattan distance 2 of a city
    fun getNearbyCity(city: City, map: GameMap): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        for(x in maxOf(city.position.first - 2, 0) .. minOf(city.position.first + 2, map.size - 1)) {
            for(y in maxOf(city.position.second - 2, 0) .. minOf(city.position.second + 2, map.size - 1)) {
                if(city.distanceTo(Pair(x, y)) <= 2 && city.position != Pair(x, y)) res.add(Pair(x, y))
            }
        }
        return res
    }

    fun getAdjacentToCity(city: City, map: GameMap): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        for(x in maxOf(city.position.first - 1, 0) .. minOf(city.position.first + 1, map.size - 1)) {
            for(y in maxOf(city.position.second - 1, 0) .. minOf(city.position.second + 1, map.size - 1)) {
                if(city.distanceTo(Pair(x, y)) <= 1 && city.position != Pair(x, y)) res.add(Pair(x, y))
            }
        }
        return res
    }

    // rate a tile (purely based on its type)
    fun rateTile(position: Pair<Int, Int>, map: GameMap): Double {
        val harvest = map.getHarvestAmounts(position)
        return harvest.food.toDouble() * 0.75 + harvest.production.toDouble() + harvest.trade.toDouble() * 0.5
    }

    // distribute already existing workers evenly
    fun distributeWorkers(
            map: GameMap,
            us: Player,
            doMoveWorker: (srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) -> Unit
    ) {
        // move workers to empty tiles
        for(c in us.cities) {
            val workers = us.workers.filter { c.distanceTo(it) <= 2 }
            val producingLocations = getNearbyCity(c, map).union(setOf(c.position))

            for(w in workers) {
                val sharesTile = us.workers.any { it.position == w.position && it != w }
                // if we share a tile with another worker, move to an unoccupied space nearby
                if(sharesTile) {
                    val moves = legalMoves(w.position, map).intersect(producingLocations).filter { move -> !us.workers.any { it.position == move } }
                    if(moves.isNotEmpty()) {
                        val move = moves.maxBy { rateTile(it, map) } ?: moves.first()
                        doMoveWorker(w.position, move)
                        w.position = move.copy()
                    }
                }
                // if we are next to the city and we can move to a producing tile (not next to the city), do it (to allow future workers to move in)
                else if (w.distanceTo(c) == 1) {
                    val moves = legalMoves(w.position, map).intersect(producingLocations).filter { move -> !us.workers.any { it.position == move } && c.distanceTo(move) > 1}
                    if(moves.isNotEmpty()) {
                        val move = moves.maxBy { rateTile(it, map) } ?: moves.first()
                        doMoveWorker(w.position, move)
                        w.position = move.copy()
                    }
                }
                // if we are on the city and can move off to an empty square, do so
                else if(w.position == c.position) {
                    val moves = legalMoves(w.position, map).filter { move -> !us.workers.any { it.position == move } }
                    if(moves.isNotEmpty()) {
                        val move = moves.maxBy { rateTile(it, map) } ?: moves.first()
                        doMoveWorker(w.position, move)
                        w.position = move.copy()
                    }
                }
            }
        }
    }

    // get a city's saturation with workers (0 - none, 1.0 - full)
    fun getCityWorkerSaturation(us: Player, city: City, map: GameMap): Double {
        val nearby = getNearbyCity(city, map).union(setOf(city.position))
        val workers = nearby.sumBy { tile -> us.workers.count{w -> w.position == tile}}
        return (workers.toDouble()) / (nearby.size.toDouble())
    }

    // rate a location for a city
    fun rateCityLocation(us: Player, them: List<Player>, location: Pair<Int, Int>, map: GameMap): Double {
        // TODO: look at nearby army + city positioning
        return (
            // tile rating
            rateTile(location, map) +
            // slightly prefer the middle
            5.0/((abs(location.first - map.size/2) + abs(location.second - map.size/2) + 1).toDouble()) +
            // strongly prefer being away from existing cities, up to distance 4 (after that, doesn't matter)
            8.0 * minOf(us.cities.map { it.distanceTo(location) }.min()?.toDouble() ?: 5.0, 5.0) +
            // strongly don't pick tiles with enemy workers or armies on them (they would capture the city)
            -100.0 * if(them.any { p -> p.armies.any {it.position == location} || p.workers.any {it.position == location} }) 1.0 else 0.0
        )
    }

    // find the best location for a city
    fun findCityLocation(us: Player, them: List<Player>, map: GameMap): Pair<Int, Int> {
        var maxPos = Pair(0, 0)
        var maxRating = 0.0
        for(x in 0 until map.size) {
            for(y in 0 until map.size) {
                if(map.contents[x][y] == TileType.Fogged) continue
                if(us.cities.any { it.position == Pair(x, y) }) continue
                if(them.any { p -> p.cities.any { it.position == Pair(x, y) } }) continue

                val rating = rateCityLocation(us, them, Pair(x, y), map)
                if(rating > maxRating) {
                    maxRating = rating
                    maxPos = Pair(x, y)
                }
            }
        }

        return maxPos
    }

    // increase offensive + defensive strength
    fun spendTrade(us: Player, doTechnology: (type: TechnologyType) -> Unit) {
        // if we have trade, increase offensive + defensive strength
        while(us.resources.trade > 20) {
            if(us.defensiveStrength >= us.offensiveStrength) {
                doTechnology(TechnologyType.Offense)
                us.resources.trade -= 20
                us.offensiveStrength += 0.1
            } else {
                doTechnology(TechnologyType.Defense)
                us.resources.trade -= 20
                us.defensiveStrength += 0.1
            }
        }
    }

    // calculate pressure on city (by opponent armies, cities, + workers)
    fun calculatePressureOnCity(us: Player, them: List<Player>, city: City, map: GameMap): Double {
        val nearby = getNearbyCity(city, map).union(setOf(city.position))
        return nearby.map { location ->
            them.map { p -> p.workers.count { w -> w.position == location} }.sum().toDouble() +
            3.0 * them.map { p -> p.cities.count { c -> c.position == location} }.sum().toDouble() +
            6.0 * them.map { p -> p.armies.count { a -> a.position == location} }.sum().toDouble()
        }.sum()
    }

    data class Threat(val position: Pair<Int, Int>, val numArmies: Int, val maxHitpoints: Int, val player: Player)
    // return a list of immediate threats to a city (army in adjacent square)--requires emergency defensive action
    // returns a list of (position, hitpoints, owning player)
    fun getImmediateThreat(us: Player, them: List<Player>, city: City, map: GameMap): List<Threat> {
        val result = mutableListOf<Threat>()
        val adjacent = getAdjacentToCity(city, map)
        adjacent.forEach {pos ->
            them.forEach {player ->
                val numArmies = player.armies.count {army -> army.position == pos }
                val maxHitpoints = player.armies.maxBy { army -> if(army.position == pos) army.hitpoints else 0 }
                if(numArmies != 0) result.add(Threat(pos, numArmies, maxHitpoints?.hitpoints ?: 0, player))
            }
        }

        return result
    }

    // move armies towards threats
    fun moveArmies(us: Player, them: List<Player>, map: GameMap, doMoveArmy: (srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) -> Unit) {
        for(a in us.armies) {
            var nearestPos = Pair(-1, -1)
            var nearestDist = 10000
            for(p in them) {
                for(ea in p.armies) {
                    if(a.distanceTo(ea) < nearestDist) {
                        nearestDist = a.distanceTo(ea)
                        nearestPos = ea.position
                    }
                }
                for(c in p.cities) {
                    if(a.distanceTo(c) < nearestDist) {
                        nearestDist = a.distanceTo(c)
                        nearestPos = c.position
                    }
                }
                for(w in p.workers) {
                    if(a.distanceTo(w) < nearestDist) {
                        nearestDist = a.distanceTo(w)
                        nearestPos = w.position
                    }
                }
            }
            if(nearestPos == Pair(-1, -1) || nearestDist > 5) continue

            // move towards target
            val diffX = nearestPos.first - a.position.first
            val diffY = nearestPos.second - a.position.second
            if(abs(diffX) > abs(diffY)) {
                doMoveArmy(a.position, Pair(a.position.first + sign(diffX.toDouble()).toInt(), a.position.second))
            } else {
                doMoveArmy(a.position, Pair(a.position.first, a.position.second + sign(diffY.toDouble()).toInt()))
            }
        }
    }


    // make a move
    // map is the game map
    // players is the list of all the players, containing their resources, cities, armies, workers
    // playerIndex is the index of you player in the players list

    // api functions:
    // doProduce is a function to call to produce something (city, army, worker)
    // doTechnology is a function to call to increase offesive or defensive strength
    // doMoveArmy moves an army from srcPos to dstPos
    // doMoveWorker moves a worker from srcPos to dstPos
    fun doMove(
            map: GameMap,
            players: List<Player>,
            playerIndex: Int,
            doProduce: (type: ProductionType, location: Pair<Int, Int>) -> Unit,
            doTechnology: (type: TechnologyType) -> Unit,
            doMoveArmy: (srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) -> Unit,
            doMoveWorker: (srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) -> Unit
    ) {

	// TODO: on doProduce, add unit/city to us.workers/us.armies/us.cities

        // split players into us and them
        val us = players[playerIndex]
        val them = players.filterIndexed { i, _ -> i != playerIndex}

        spendTrade(us, doTechnology)

        // if beginning of game, and we have no workers, create one instead of an army (stops us falling a few turns behind)
        if(us.cities.size == 1 && us.workers.size == 0 && us.resources.production >= 8) {
            doProduce(ProductionType.Worker, us.cities[0].position)
            us.workers.add(Worker(us.cities[0].position))
            us.resources.production -= 8
        }

        // calculate food left over before the 1/4 removal
        var leftOverFood = (us.resources.food * 4)/3

        // if under attack, create defenses
        for(c in us.cities) {
            val threats = getImmediateThreat(us, them, c, map)
            if(threats.size > 0) {
                for(t in threats) {
                    // calculate needed forces to attack and kill them
                    val numArmies = t.maxHitpoints.toDouble()/(((us.offensiveStrength/t.player.defensiveStrength)*((map.getCombatMultiplier(c.position)!!*1.5)/map.getCombatMultiplier(t.position)!!))/t.numArmies.toDouble())
                    var realNumArmies = ceil(numArmies.toDouble()/100.0).toInt()
                    for(i in 0 until realNumArmies) {
                        if(us.resources.production < 8) break
                        doProduce(ProductionType.Army, c.position)
                        doMoveArmy(c.position, t.position)
                        us.resources.production -= 8
                    }
                }
            }
        }

        // if we have more than 15 production this turn, save some (for emergency defense creation)
        var emergencyProduction = 0
        if(us.resources.production >= 15) {
            if(us.resources.production < 100) emergencyProduction = us.resources.production / 5
            else emergencyProduction = us.resources.production / 3
            us.resources.production -= emergencyProduction
        }

        // if we don't have an army on existing cities, add them
        for(c in us.cities) {
            val existingArmy = us.armies.any { it.position == c.position }
            if(existingArmy) continue
            // if we have enough production and food, create the army
            if(us.resources.production >= 8 && leftOverFood > 0) {
                doProduce(ProductionType.Army, c.position)
                us.armies.add(Army(c.position, 100))
                us.resources.production -= 8
                leftOverFood -= 1
            }
        }

        // if we can create a worker and city's aren't saturated, do so
        while(us.resources.production >= 8) {
            // spread workers out between cities (so that we take minimal loss if a city is captured)
            // if a city is pressured, don't give it workers (instead remove pressure than add workers)
            val saturation = us.cities.map {city ->
                getCityWorkerSaturation(us, city, map) + calculatePressureOnCity(us, them, city, map)/3.0
            }


            val minIndex = saturation.indices.minBy { saturation[it] } ?: 0

            // only create worker if city isn't already saturated
            if(saturation[minIndex] < 0.9) {
                doProduce(ProductionType.Worker, us.cities[minIndex].position)
                us.workers.add(Worker(us.cities[minIndex].position))
                us.resources.production -= 8
            } else {
                break
            }
        }

        // if we can create a new city, do it (if city defending armies + other cities are saturated with workers)
        var didCity = false
        if(us.resources.production >= 24) {
            val cityLoc = findCityLocation(us, them, map)
            doProduce(ProductionType.City, cityLoc)
            us.cities.add(City(cityLoc))
            us.resources.production -= 24
            didCity = true
        }

        if(!didCity) us.resources.production /= 2

        // create armies with left over resources
        while(us.resources.production >= 8 && leftOverFood > 0) {
            val pressure = us.cities.map {city ->
                calculatePressureOnCity(us, them, city, map)
            }

            val maxIndex = pressure.indices.maxBy { pressure[it] } ?: 0
            val city = us.cities[maxIndex]

            doProduce(ProductionType.Army, city.position)
            us.resources.production -= 8
            leftOverFood -= 1
        }

        distributeWorkers(map, us, doMoveWorker)
        moveArmies(us, them, map, doMoveArmy)
    }
}
