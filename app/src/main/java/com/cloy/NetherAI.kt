package com.cloy

import android.util.Log
import java.util.*
import kotlin.math.*

/**
 * Nether World AI Engine
 *
 * Nether mechanics (from wiki research):
 * - 4 Keys scattered randomly → unlock Exit Gate → keep all Gems
 * - Die without keys → lose all Gems (or 25% with Gem Rescue perk)
 * - Enemies: melee, ranged, patrol (each has different AI)
 * - Traps: fire (instant kill), arrow, spike — wait for fire before moving
 * - Prize Boxes: Nether Crystals
 * - Priority: Survive → Keys → Crystals → Gems → Exit
 *
 * Strategy (from PCAP analysis):
 * - Attack: TiLT {x,y} = swing weapon at tile
 * - Move:   yGLu {Nxir: <x(i32LE) y(i32LE)>}
 * - Tick:   GnkD every ~100ms
 * - BFS: find walkable path avoiding enemies + traps
 * - Jitter detection: vary attack timing ±50ms to avoid pattern detection
 */
class NetherAI(
    private val protocol: PWProtocol,
    private val onStatus: (String) -> Unit,
    private val onStats: (NetherStats) -> Unit
) {
    companion object {
        const val TAG = "NetherAI"

        // Block IDs (from world data analysis)
        const val BLOCK_EMPTY     = 0
        const val BLOCK_NETHER_KEY = 3990   // Nether key block
        const val BLOCK_EXIT_GATE  = 3989   // Exit gate
        const val BLOCK_PRIZE_BOX  = 3991   // Crystal prize box
        const val BLOCK_FIRE_TRAP  = 3992   // Fire trap (instant kill)
        const val BLOCK_ARROW_TRAP = 3993   // Arrow trap
        const val BLOCK_SOLID      = 8      // Unbreakable

        // Enemy block IDs (entities visible in world)
        const val ENTITY_MELEE   = 4001
        const val ENTITY_RANGED  = 4002
        const val ENTITY_PATROL  = 4003

        // Timings (ms)
        const val ATTACK_BASE_MS   = 350L
        const val ATTACK_JITTER_MS = 80L
        const val MOVE_TICK_MS     = 120L
        const val TRAP_FIRE_WAIT   = 1200L
        const val TRAP_ARROW_WAIT  = 800L
        const val HP_HEAL_THRESHOLD = 30   // heal when HP below this %

        // Item IDs
        const val ITEM_WEAPON   = 4087
        const val ITEM_POTION   = 3950
    }

    data class NetherStats(
        var keysCollected: Int = 0,
        var keysNeeded: Int = 4,
        var gemsCollected: Int = 0,
        var crystalsCollected: Int = 0,
        var enemiesKilled: Int = 0,
        var runsCompleted: Int = 0,
        var hp: Int = 100,
        var maxHp: Int = 100,
        var phase: String = "idle"
    )

    data class Entity(
        val id: String,
        var x: Int,
        var y: Int,
        val type: Int,     // ENTITY_MELEE / RANGED / PATROL
        var hp: Int = 100,
        var lastSeenMs: Long = System.currentTimeMillis()
    )

    data class Point(val x: Int, val y: Int)

    private val stats = NetherStats()
    private val entities = mutableMapOf<String, Entity>()
    private val keys = mutableListOf<Point>()
    private val crystalBoxes = mutableListOf<Point>()
    private var exitGate: Point? = null
    private var running = false
    private var currentPath = ArrayDeque<Point>()
    private val random = Random()
    private val trapCooldowns = mutableMapOf<Point, Long>() // trap → last fire time

    // State machine
    private enum class Phase { IDLE, SCAN_WORLD, KILL_NEARBY, MOVE_TO_KEY, COLLECT_KEY,
        MOVE_TO_CRYSTAL, COLLECT_CRYSTAL, MOVE_TO_EXIT, EXIT, HEAL }

    private var phase = Phase.IDLE

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    fun start() {
        running = true
        stats.phase = "starting"
        onStatus("NetherAI started")
        aiLoop()
    }

    fun stop() {
        running = false
        stats.phase = "idle"
        phase = Phase.IDLE
    }

    fun onWorldData(tiles: IntArray, w: Int, h: Int) {
        scanWorld(tiles, w, h)
        if (phase == Phase.IDLE || phase == Phase.SCAN_WORLD) {
            phase = Phase.KILL_NEARBY
        }
    }

    fun onPacket(id: String, pkt: org.bson.BsonDocument) {
        when (id) {
            // Entity updates from server
            "lynM", "eIgm" -> updateEntities(id, pkt)
            // Block hit result (enemy took damage)
            "nHCR" -> {
                val x = pkt.getInt32("x").value
                val y = pkt.getInt32("y").value
                val blockType = if (pkt.containsKey("jrFN")) pkt.getInt32("jrFN").value else 0
                onBlockBroken(x, y, blockType)
            }
            // Collectable spawned
            "?" -> {
                val cid = if (pkt.containsKey("CollectableID")) pkt.getInt32("CollectableID").value else 0
                protocol.collect(cid)
            }
            // Trap fired
            "cJeE" -> onTrapFired(pkt)
            // HP update
            "wWue" -> {
                if (pkt.containsKey("JFRu")) stats.hp = pkt.getInt32("JFRu").value
            }
            // Key collected confirmation
            "DEpC" -> {
                stats.keysCollected++
                onStatus("🗝️ Key ${stats.keysCollected}/4 collected!")
                onStats(stats)
                keys.removeFirstOrNull()
                if (stats.keysCollected >= 4) phase = Phase.MOVE_TO_EXIT
            }
        }
    }

    // ── AI LOOP ───────────────────────────────────────────────────────────────

    private fun aiLoop() = Thread({
        while (running) {
            try {
                tick()
                Thread.sleep(MOVE_TICK_MS)
            } catch (e: InterruptedException) { break }
              catch (e: Exception) { Log.e(TAG, "AI tick error: ${e.message}") }
        }
    }, "NetherAI").start()

    private fun tick() {
        // Always send GnkD tick
        protocol.tick(ts())

        // HP check — heal if needed
        if (stats.hp < HP_HEAL_THRESHOLD && phase != Phase.HEAL) {
            phase = Phase.HEAL
        }

        when (phase) {
            Phase.IDLE        -> { /* waiting for world data */ }
            Phase.SCAN_WORLD  -> { /* scanning done in onWorldData */ }
            Phase.HEAL        -> doHeal()
            Phase.KILL_NEARBY -> killNearbyEnemies()
            Phase.MOVE_TO_KEY -> moveToNextKey()
            Phase.COLLECT_KEY -> collectKey()
            Phase.MOVE_TO_CRYSTAL -> moveToCrystal()
            Phase.COLLECT_CRYSTAL -> collectCrystal()
            Phase.MOVE_TO_EXIT -> moveToExit()
            Phase.EXIT        -> { /* done */ }
        }

        onStats(stats)
    }

    // ── PHASES ────────────────────────────────────────────────────────────────

    private fun killNearbyEnemies() {
        stats.phase = "killing enemies"
        val px = protocol.playerX.toInt()
        val py = protocol.playerY.toInt()

        // Find nearest enemy within attack range (3 tiles)
        val nearbyEnemy = entities.values
            .filter { abs(it.x - px) <= 3 && abs(it.y - py) <= 3 }
            .minByOrNull { dist(it.x, it.y, px, py) }

        if (nearbyEnemy != null) {
            // Attack with jitter to avoid detection
            val jitter = random.nextInt(ATTACK_JITTER_MS.toInt().coerceAtLeast(1)).toLong()
            Thread.sleep(ATTACK_BASE_MS + jitter - ATTACK_JITTER_MS / 2)

            // Target and attack
            protocol.selectTile(nearbyEnemy.x, nearbyEnemy.y)
            protocol.attack(nearbyEnemy.x, nearbyEnemy.y)
            protocol.useItem(ITEM_WEAPON)
            onStatus("⚔️ Attacking enemy at (${nearbyEnemy.x},${nearbyEnemy.y})")
        } else {
            // No nearby enemies — move to next priority
            phase = when {
                keys.isNotEmpty() -> Phase.MOVE_TO_KEY
                crystalBoxes.isNotEmpty() -> Phase.MOVE_TO_CRYSTAL
                exitGate != null && stats.keysCollected >= 4 -> Phase.MOVE_TO_EXIT
                else -> Phase.SCAN_WORLD
            }
        }
    }

    private fun moveToNextKey() {
        if (keys.isEmpty()) {
            phase = if (stats.keysCollected >= 4) Phase.MOVE_TO_EXIT else Phase.SCAN_WORLD
            return
        }
        stats.phase = "going to key ${stats.keysCollected + 1}/4"
        val target = keys.first()
        val px = protocol.playerX.toInt()
        val py = protocol.playerY.toInt()

        if (dist(px, py, target.x, target.y) < 2f) {
            phase = Phase.COLLECT_KEY; return
        }

        // Check for enemies on path first
        val nearbyEnemy = entities.values.minByOrNull { dist(it.x, it.y, px, py) }
        if (nearbyEnemy != null && dist(nearbyEnemy.x, nearbyEnemy.y, px, py) < 4f) {
            phase = Phase.KILL_NEARBY; return
        }

        // BFS path to key
        val path = bfs(Point(px, py), target) ?: run {
            onStatus("⚠️ No path to key at (${target.x},${target.y})")
            keys.removeFirst(); return
        }

        if (path.isNotEmpty()) {
            val next = path.first()
            // Check for traps before moving
            if (isTrapDangerous(next)) {
                onStatus("🔥 Trap at (${next.x},${next.y}), waiting…")
                Thread.sleep(TRAP_FIRE_WAIT)
                return
            }
            protocol.move(next.x, next.y)
        }
    }

    private fun collectKey() {
        if (keys.isEmpty()) { phase = Phase.KILL_NEARBY; return }
        val key = keys.first()
        protocol.selectTile(key.x, key.y)
        protocol.attack(key.x, key.y)
        onStatus("🗝️ Collecting key at (${key.x},${key.y})…")
        Thread.sleep(400)
    }

    private fun moveToCrystal() {
        if (crystalBoxes.isEmpty()) {
            phase = if (stats.keysCollected >= 4) Phase.MOVE_TO_EXIT else Phase.MOVE_TO_KEY
            return
        }
        stats.phase = "collecting crystals (${stats.crystalsCollected})"
        val target = crystalBoxes.first()
        val px = protocol.playerX.toInt()
        val py = protocol.playerY.toInt()

        if (dist(px, py, target.x, target.y) < 2f) {
            phase = Phase.COLLECT_CRYSTAL; return
        }
        val path = bfs(Point(px, py), target) ?: run { crystalBoxes.removeFirst(); return }
        if (path.isNotEmpty()) protocol.move(path.first().x, path.first().y)
    }

    private fun collectCrystal() {
        if (crystalBoxes.isEmpty()) { phase = Phase.MOVE_TO_KEY; return }
        val box = crystalBoxes.first()
        protocol.selectTile(box.x, box.y)
        protocol.attack(box.x, box.y)
        crystalBoxes.removeFirst()
        stats.crystalsCollected += 4
        onStatus("💎 Crystal box collected! Total: ${stats.crystalsCollected}")
        phase = Phase.MOVE_TO_KEY
    }

    private fun moveToExit() {
        val exit = exitGate ?: run { onStatus("⚠️ Exit not found!"); return }
        stats.phase = "going to exit"
        val px = protocol.playerX.toInt()
        val py = protocol.playerY.toInt()

        if (dist(px, py, exit.x, exit.y) < 2f) {
            // At exit — enter door
            protocol.selectTile(exit.x, exit.y)
            protocol.attack(exit.x, exit.y)
            val enterDoor = org.bson.BsonDocument()
            enterDoor["ID"] = org.bson.BsonString("EnD")
            enterDoor["x"]  = org.bson.BsonInt32(exit.x)
            enterDoor["y"]  = org.bson.BsonInt32(exit.y)
            protocol.queue(enterDoor)
            protocol.flush()
            phase = Phase.EXIT
            stats.runsCompleted++
            onStatus("✅ Run complete! Gems: ${stats.gemsCollected}, Crystals: ${stats.crystalsCollected}")
            onStats(stats)
            return
        }

        val path = bfs(Point(px, py), exit) ?: run { onStatus("No path to exit!"); return }
        if (path.isNotEmpty()) {
            val next = path.first()
            if (isTrapDangerous(next)) { Thread.sleep(TRAP_FIRE_WAIT); return }
            protocol.move(next.x, next.y)
        }
    }

    private fun doHeal() {
        stats.phase = "healing (HP: ${stats.hp}%)"
        protocol.useItem(ITEM_POTION)
        onStatus("💊 Healing… HP: ${stats.hp}%")
        Thread.sleep(500)
        stats.hp = minOf(stats.hp + 40, stats.maxHp)
        phase = Phase.KILL_NEARBY
    }

    // ── BFS PATHFINDING ───────────────────────────────────────────────────────

    /**
     * BFS to find shortest walkable path from start to goal.
     * Avoids: solid blocks, active traps, enemies (if possible).
     * Returns list of Points to walk through (first step first).
     */
    private fun bfs(start: Point, goal: Point): List<Point>? {
        val w = protocol.worldWidth; val h = protocol.worldHeight
        val tiles = protocol.worldFg
        if (tiles.isEmpty()) return null

        val visited = Array(h) { BooleanArray(w) }
        val parent  = HashMap<Point, Point?>()
        val queue   = ArrayDeque<Point>()

        queue.add(start); visited[start.y.coerceIn(0, h-1)][start.x.coerceIn(0, w-1)] = true
        parent[start] = null

        val dirs = arrayOf(Point(1,0), Point(-1,0), Point(0,1), Point(0,-1))

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == goal || (abs(cur.x - goal.x) <= 1 && abs(cur.y - goal.y) <= 1)) {
                // Reconstruct path
                val path = mutableListOf<Point>()
                var node: Point? = cur
                while (node != null && node != start) {
                    path.add(0, node); node = parent[node]
                }
                return path
            }
            for (d in dirs) {
                val nx = cur.x + d.x; val ny = cur.y + d.y
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                if (visited[ny][nx]) continue
                val tile = tiles[nx + ny * w]
                if (isBlocking(tile, Point(nx, ny))) continue
                visited[ny][nx] = true
                val np = Point(nx, ny)
                parent[np] = cur
                queue.add(np)
            }
        }
        return null
    }

    private fun isBlocking(tile: Int, pos: Point): Boolean {
        if (tile != BLOCK_EMPTY && tile != BLOCK_NETHER_KEY && tile != BLOCK_EXIT_GATE
            && tile != BLOCK_PRIZE_BOX && tile < 3989) return true
        // Avoid enemies
        if (entities.values.any { it.x == pos.x && it.y == pos.y }) return true
        // Avoid active fire traps
        if (tile == BLOCK_FIRE_TRAP && isTrapDangerous(pos)) return true
        return false
    }

    private fun isTrapDangerous(pos: Point): Boolean {
        val lastFire = trapCooldowns[pos] ?: return false
        return System.currentTimeMillis() - lastFire < TRAP_FIRE_WAIT
    }

    // ── WORLD SCANNING ────────────────────────────────────────────────────────

    private fun scanWorld(tiles: IntArray, w: Int, h: Int) {
        keys.clear(); crystalBoxes.clear(); exitGate = null
        for (y in 0 until h) {
            for (x in 0 until w) {
                when (tiles[x + y * w]) {
                    BLOCK_NETHER_KEY -> keys.add(Point(x, y))
                    BLOCK_EXIT_GATE  -> exitGate = Point(x, y)
                    BLOCK_PRIZE_BOX  -> crystalBoxes.add(Point(x, y))
                }
            }
        }
        onStatus("🗺️ Scanned: ${keys.size} keys, ${crystalBoxes.size} boxes, exit=${exitGate != null}")
        stats.keysNeeded = 4
    }

    private fun onBlockBroken(x: Int, y: Int, blockType: Int) {
        // Remove entity at this position
        entities.entries.removeIf { (_, e) -> e.x == x && e.y == y }
        if (blockType != 0) stats.enemiesKilled++
        onStats(stats)
    }

    private fun onTrapFired(pkt: org.bson.BsonDocument) {
        // Record trap fire time for BFS avoidance
        if (pkt.containsKey("x") && pkt.containsKey("y")) {
            val pos = Point(pkt.getInt32("x").value, pkt.getInt32("y").value)
            trapCooldowns[pos] = System.currentTimeMillis()
        }
    }

    private fun updateEntities(id: String, pkt: org.bson.BsonDocument) {
        val nfim = if (pkt.containsKey("NfIM")) pkt.getString("NfIM").value else return
        val x = if (pkt.containsKey("x")) pkt.getInt32("x").value else return
        val y = if (pkt.containsKey("y")) pkt.getInt32("y").value else return
        val existing = entities[nfim]
        if (existing != null) { existing.x = x; existing.y = y; existing.lastSeenMs = System.currentTimeMillis() }
        else entities[nfim] = Entity(nfim, x, y, ENTITY_MELEE)
    }

    private fun dist(x1: Int, y1: Int, x2: Int, y2: Int) =
        sqrt(((x1 - x2).toDouble().pow(2) + (y1 - y2).toDouble().pow(2))).toFloat()

    private fun dist(p1: Point, p2: Point) = dist(p1.x, p1.y, p2.x, p2.y)

    private fun ts() = System.currentTimeMillis() * 10_000L + 621_355_968_000_000_000L

    private operator fun org.bson.BsonDocument.set(k: String, v: org.bson.BsonValue) { put(k, v) }
}
