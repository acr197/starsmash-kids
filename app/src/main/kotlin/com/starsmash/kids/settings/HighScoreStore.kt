package com.starsmash.kids.settings

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single high score entry on the leaderboard.
 *
 * @property name   Player-entered name, max 20 characters, sanitised on entry.
 * @property score  Number of targets smashed in that session.
 * @property epochMs Unix millis when the score was recorded.
 */
data class HighScoreEntry(
    val name: String,
    val score: Int,
    val epochMs: Long
) {
    /** Human-readable formatted date like "Apr 8, 2026". */
    fun formattedDate(): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }
}

/**
 * High score storage backed by SharedPreferences. Keeps the top [MAX_ENTRIES]
 * scores sorted descending by score (ties broken by most recent). Encoded as
 * a simple CSV line-per-entry blob so there are no dependencies on JSON.
 *
 * Format of each stored line: `score|epochMs|name`
 *   - `|` separator
 *   - names with `|` or newline are stripped at save time.
 *
 * Thread safety: SharedPreferences itself is thread-safe for single writes.
 * Callers are expected to interact with this from the UI / view-model scope.
 */
object HighScoreStore {

    const val MAX_ENTRIES = 10
    const val MAX_NAME_LENGTH = 20

    private const val PREFS_NAME = "starsmash_high_scores"
    private const val KEY_SCORES = "scores_v1"

    /** Load the current high score list, sorted descending by score. */
    fun load(context: Context): List<HighScoreEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blob = prefs.getString(KEY_SCORES, null) ?: return emptyList()
        return blob.split('\n')
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val score = parts[0].toIntOrNull() ?: return@mapNotNull null
                val epoch = parts[1].toLongOrNull() ?: return@mapNotNull null
                HighScoreEntry(name = parts[2], score = score, epochMs = epoch)
            }
            .sortedWith(compareByDescending<HighScoreEntry> { it.score }.thenByDescending { it.epochMs })
    }

    /**
     * Returns true if [score] would place in the top [MAX_ENTRIES].
     */
    fun isHighScore(context: Context, score: Int): Boolean {
        if (score <= 0) return false
        val list = load(context)
        if (list.size < MAX_ENTRIES) return true
        return score > (list.lastOrNull()?.score ?: 0)
    }

    /**
     * Save a new score with the given name. The name is sanitised (stripped
     * of pipes and newlines, trimmed, capped at [MAX_NAME_LENGTH] chars).
     * The list is re-sorted and truncated to [MAX_ENTRIES].
     */
    fun save(context: Context, name: String, score: Int) {
        val cleanName = name
            .replace('|', ' ')
            .replace('\n', ' ')
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifEmpty { "Player" }
        val entry = HighScoreEntry(cleanName, score, System.currentTimeMillis())
        val merged = (load(context) + entry)
            .sortedWith(compareByDescending<HighScoreEntry> { it.score }.thenByDescending { it.epochMs })
            .take(MAX_ENTRIES)
        val blob = merged.joinToString("\n") { "${it.score}|${it.epochMs}|${it.name}" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCORES, blob)
            .apply()
    }

    /** Remove all saved scores. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SCORES)
            .apply()
    }
}
