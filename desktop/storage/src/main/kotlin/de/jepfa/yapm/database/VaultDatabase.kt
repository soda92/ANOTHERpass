package de.jepfa.yapm.database

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database with the same schema as the Android app (`DB_VERSION = 10`).
 *
 * The schema is plain SQLite: all sensitive columns already contain
 * app-level-encrypted [de.jepfa.yapm.model.encrypted.Encrypted] base64 strings,
 * exactly like Android. No SQLCipher is required.
 */
class VaultDatabase private constructor(private val connection: Connection) : AutoCloseable {

    private val lock = object {}

    fun <T> withConnection(block: (Connection) -> T): T = synchronized(lock) {
        block(connection)
    }

    /**
     * Runs [block] in a single transaction. Repository calls performed inside the
     * block reuse this thread's connection and therefore join the transaction.
     */
    fun <T> transaction(block: () -> T): T = withConnection { c ->
        c.autoCommit = false
        try {
            block().also { c.commit() }
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun lastInsertRowId(connection: Connection): Int {
        connection.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            connection.close()
        }
    }

    private fun initialize() {
        val version = userVersion()
        if (!tableExists(TABLE_CREDENTIAL)) {
            createSchema()
        } else if (version < DB_VERSION) {
            migrate(version)
        } else if (version > DB_VERSION) {
            throw IllegalStateException(
                "Database version $version is newer than supported $DB_VERSION"
            )
        }
    }

    private fun createSchema() {
        withConnection { c ->
            c.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS `EncCredentialEntity` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `uid` TEXT,
                      `name` TEXT NOT NULL,
                      `additionalInfo` TEXT NOT NULL,
                      `user` TEXT NOT NULL,
                      `password` TEXT NOT NULL,
                      `lastPassword` TEXT,
                      `website` TEXT NOT NULL,
                      `labels` TEXT NOT NULL,
                      `expiresAt` TEXT,
                      `isObfuscated` INTEGER NOT NULL,
                      `isLastPasswordObfuscated` INTEGER NOT NULL,
                      `otpData` TEXT,
                      `modifyTimestamp` INTEGER NOT NULL,
                      `pinned` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_EncCredentialEntity_uid` " +
                        "ON `EncCredentialEntity` (`uid`)"
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS `EncLabelEntity` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `uid` TEXT,
                      `name` TEXT NOT NULL,
                      `description` TEXT NOT NULL,
                      `color` INTEGER
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_EncLabelEntity_uid` " +
                        "ON `EncLabelEntity` (`uid`)"
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS `EncUsernameTemplateEntity` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `username` TEXT NOT NULL,
                      `description` TEXT NOT NULL,
                      `generatorType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS `EncWebExtensionEntity` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `webClientId` TEXT NOT NULL,
                      `title` TEXT NOT NULL,
                      `extensionPublicKey` TEXT NOT NULL,
                      `sharedBaseKey` TEXT NOT NULL,
                      `linked` INTEGER NOT NULL,
                      `enabled` INTEGER NOT NULL,
                      `bypassIncomingRequests` INTEGER NOT NULL,
                      `lastUsedTimestamp` INTEGER
                    )
                    """.trimIndent()
                )
                st.executeUpdate("PRAGMA user_version = $DB_VERSION")
            }
        }
    }

    /** Applies the Android migrations in order, guarded by current columns. */
    private fun migrate(fromVersion: Int) {
        withConnection { c ->
            c.autoCommit = false
            try {
                if (fromVersion < 2 && !columnExists(c, TABLE_CREDENTIAL, "lastPassword")) {
                    c.createStatement().executeUpdate("ALTER TABLE EncCredentialEntity ADD COLUMN lastPassword TEXT")
                }
                if (fromVersion < 3) {
                    if (!columnExists(c, TABLE_CREDENTIAL, "isObfuscated")) {
                        c.createStatement().executeUpdate(
                            "ALTER TABLE EncCredentialEntity ADD COLUMN isObfuscated INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                    if (!columnExists(c, TABLE_CREDENTIAL, "isLastPasswordObfuscated")) {
                        c.createStatement().executeUpdate(
                            "ALTER TABLE EncCredentialEntity ADD COLUMN isLastPasswordObfuscated INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }
                if (fromVersion < 4 && !columnExists(c, TABLE_CREDENTIAL, "modifyTimestamp")) {
                    c.createStatement().executeUpdate(
                        "ALTER TABLE EncCredentialEntity ADD COLUMN modifyTimestamp INTEGER NOT NULL DEFAULT 0"
                    )
                    c.createStatement().executeUpdate("UPDATE EncCredentialEntity SET modifyTimestamp=id")
                }
                if (fromVersion < 5) {
                    if (!columnExists(c, TABLE_CREDENTIAL, "uid")) {
                        c.createStatement().executeUpdate("ALTER TABLE EncCredentialEntity ADD COLUMN uid TEXT")
                    }
                    if (!columnExists(c, TABLE_LABEL, "uid")) {
                        c.createStatement().executeUpdate("ALTER TABLE EncLabelEntity ADD COLUMN uid TEXT")
                    }
                    c.createStatement().executeUpdate(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_EncCredentialEntity_uid ON EncCredentialEntity(uid)"
                    )
                    c.createStatement().executeUpdate(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_EncLabelEntity_uid ON EncLabelEntity(uid)"
                    )
                }
                if (fromVersion < 6 && !columnExists(c, TABLE_CREDENTIAL, "expiresAt")) {
                    c.createStatement().executeUpdate("ALTER TABLE EncCredentialEntity ADD COLUMN expiresAt TEXT")
                }
                if (fromVersion < 7) {
                    c.createStatement().executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS `EncUsernameTemplateEntity` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `username` TEXT NOT NULL,
                          `description` TEXT NOT NULL,
                          `generatorType` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }
                if (fromVersion < 8) {
                    c.createStatement().executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS `EncWebExtensionEntity` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `webClientId` TEXT NOT NULL,
                          `title` TEXT NOT NULL,
                          `extensionPublicKey` TEXT NOT NULL,
                          `sharedBaseKey` TEXT NOT NULL,
                          `linked` INTEGER NOT NULL,
                          `enabled` INTEGER NOT NULL,
                          `bypassIncomingRequests` INTEGER NOT NULL,
                          `lastUsedTimestamp` INTEGER
                        )
                        """.trimIndent()
                    )
                }
                if (fromVersion < 9 && !columnExists(c, TABLE_CREDENTIAL, "otpData")) {
                    c.createStatement().executeUpdate("ALTER TABLE EncCredentialEntity ADD COLUMN otpData TEXT")
                }
                if (fromVersion < 10 && !columnExists(c, TABLE_CREDENTIAL, "pinned")) {
                    c.createStatement().executeUpdate(
                        "ALTER TABLE EncCredentialEntity ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"
                    )
                }
                c.createStatement().executeUpdate("PRAGMA user_version = $DB_VERSION")
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    private fun userVersion(): Int = withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("PRAGMA user_version").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private fun tableExists(table: String): Boolean = withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$table'"
            ).use { it.next() }
        }
    }

    private fun columnExists(c: Connection, table: String, column: String): Boolean {
        c.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) return true
                }
            }
        }
        return false
    }

    companion object {
        const val DB_VERSION = 10
        const val TABLE_CREDENTIAL = "EncCredentialEntity"
        const val TABLE_LABEL = "EncLabelEntity"
        const val TABLE_USERNAME_TEMPLATE = "EncUsernameTemplateEntity"
        const val TABLE_WEB_EXTENSION = "EncWebExtensionEntity"

        fun open(path: Path): VaultDatabase {
            path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            val connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            return VaultDatabase(connection).also { it.initialize() }
        }

        fun inMemory(): VaultDatabase {
            val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            return VaultDatabase(connection).also { it.initialize() }
        }
    }
}
