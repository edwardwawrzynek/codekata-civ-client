package app

import kotlin.system.exitProcess
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.google.gson.Gson
import kotlin.concurrent.thread

/**
 * DON'T EDIT THIS FILE (edit AI.kt instead)
 *
 * This file contains the Runner class, which manages the connection with the server
 */

class Runner(val ai: AI, val apiUrl: String, val apiKey: String) {
    val api = API(apiUrl, apiKey)
    var refreshRate = 1000

    fun loadRefreshRate() {
        val info = api.getInfo()
        if(info.error != null) println("Error loading /api/info -- ${info.error}")
        else {
            println("Connected to server ${apiUrl} with key ${apiKey}.\nServer version: ${info.version}. Server requests refresh rate ${info.playerRefreshRate} ms")
            refreshRate = info.playerRefreshRate
        }
    }

    fun sendName() {
        val resp = api.setName(ai.getName())
        if(resp.error != null) println("Failure setting name: ${resp.error}")
    }

    fun getPlayerIndex(): Int? {
        val resp = api.getPlayerIndex()
        if(resp.error != null) {
            println("Failure getting player index: ${resp.error}")
            return null
        }
        println("Player is player #${resp.player}")
        return resp.player
    }

    fun getBoard(): GameMap? {
        val resp = api.getBoard()
        if(resp.error != null) {
            println("Failure loading board: ${resp.error}")
            return null
        }
        val contents = Array(resp.size) { Array(resp.size) { TileType.Fogged } }
        for(x in 0 until resp.size) {
            for(y in 0 until resp.size) {
                contents[x][y] = when(resp.board[x][y]) {
                    0 -> TileType.Ocean
                    1 -> TileType.Grassland
                    2 -> TileType.Hills
                    3 -> TileType.Forest
                    4 -> TileType.Mountains
                    else -> TileType.Fogged
                }
            }
        }
        return GameMap(resp.size, contents)
    }

    fun waitUntilTurn(playerIndex: Int) {
        do {
            Thread.sleep(refreshRate.toLong(), 0)
            val resp = api.getCurrentPlayer()
            if(resp.error != null) println("Error getting current player: ${resp.error}")
            if(resp.turn != playerIndex) println("Waiting for turn. Current player is: ${resp.turn}, we are: ${playerIndex}")
        } while(resp.turn != playerIndex)
    }

    fun endTurn() = api.endTurn()

    fun loadPlayers(): List<Player> {
        val cities = api.getCities()
        val armies = api.getArmies()
        val workers = api.getWorkers()
        val resources = api.getResources()
        val players = api.getPlayers()

        return (0 until 4).map { i ->
            val pCities = cities.cities[i].map {c -> City(Pair(c.x, c.y))}
            val pArmies = armies.armies[i].map {a -> Army(Pair(a.x, a.y), a.hitpoints)}
            val pWorkers = workers.workers[i].map {w -> Worker(Pair(w.x, w.y))}
            val pResources = ResourceAmounts(resources.resources[i].food, resources.resources[i].production, resources.resources[i].trade)

            val mCities = mutableListOf<City>()
            mCities.addAll(pCities)
            val mArmies = mutableListOf<Army>()
            mArmies.addAll(pArmies)
            val mWorkers = mutableListOf<Worker>()
            mWorkers.addAll(pWorkers)

            Player(players.players[i].offense, players.players[i].defense, mCities, mWorkers, mArmies, pResources)
        }
    }

    fun doProduce(type: ProductionType, location: Pair<Int, Int>) {
        val resp = api.doProduce(type, location)
        if(resp.error != null) println("Error doing production: ${resp.error}")
    }

    fun doTechnology(type: TechnologyType) {
        val resp = api.doTechnology(type)
        if(resp.error != null) println("Error doing technology: ${resp.error}")
    }

    fun doMoveArmy(srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) {
        val resp = api.doMoveArmy(srcPos, dstPos)
        if(resp.error != null) println("Error moving army: ${resp.error}")
    }

    fun doMoveWorker(srcPos: Pair<Int, Int>, dstPos: Pair<Int, Int>) {
        val resp = api.doMoveWorker(srcPos, dstPos)
        if(resp.error != null) println("Error moving worker: ${resp.error}")
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            if(args.size < 2) {
                println("Expected API url as first command line argument and API key as second")
                exitProcess(1)
            }

            for(i in 1 until args.size) {
                thread {
                    val run = Runner(AI(), args[0], args[i])
                    println("Starting. API URL: ${args[0]}, API KEY: ${args[1]}")
                    println("Connecting to server...")
                    run.loadRefreshRate()
                    run.sendName()
                    val index = run.getPlayerIndex()
                    while(true) {
                        run.waitUntilTurn(index!!)
                        println("Starting Turn")
                        val map = run.getBoard()
                        val players = run.loadPlayers()
                        run.ai.doMove(map!!, players, index, run::doProduce, run::doTechnology, run::doMoveArmy, run::doMoveWorker)
                        println("Ending turn")
                        run.endTurn()
                    }
                }
            }
        }
    }
}
