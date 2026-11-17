package de.jepfa.yapm.database.repository

import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.model.encrypted.EncWebExtension
import java.sql.ResultSet

class WebExtensionRepository(private val db: VaultDatabase) {

    fun insert(extension: EncWebExtension): EncWebExtension = db.withConnection { c ->
        val sql = """
            INSERT INTO EncWebExtensionEntity
              (id, webClientId, title, extensionPublicKey, sharedBaseKey, linked, enabled,
               bypassIncomingRequests, lastUsedTimestamp)
            VALUES (?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, extension.id)
            ps.setString(2, extension.webClientId.toBase64String())
            ps.setString(3, extension.title.toBase64String())
            ps.setString(4, extension.extensionPublicKey.toBase64String())
            ps.setString(5, extension.sharedBaseKey.toBase64String())
            ps.setInt(6, if (extension.linked) 1 else 0)
            ps.setInt(7, if (extension.enabled) 1 else 0)
            ps.setInt(8, if (extension.bypassIncomingRequests) 1 else 0)
            ps.setObject(9, extension.lastUsedTimestamp)
            ps.executeUpdate()
        }
        extension.id = db.lastInsertRowId(c)
        extension
    }

    fun update(extension: EncWebExtension): Boolean = db.withConnection { c ->
        val id = extension.id ?: return@withConnection false
        val sql = """
            UPDATE EncWebExtensionEntity SET webClientId=?, title=?, extensionPublicKey=?,
              sharedBaseKey=?, linked=?, enabled=?, bypassIncomingRequests=?, lastUsedTimestamp=?
            WHERE id=?
        """.trimIndent()
        c.prepareStatement(sql).use { ps ->
            ps.setString(1, extension.webClientId.toBase64String())
            ps.setString(2, extension.title.toBase64String())
            ps.setString(3, extension.extensionPublicKey.toBase64String())
            ps.setString(4, extension.sharedBaseKey.toBase64String())
            ps.setInt(5, if (extension.linked) 1 else 0)
            ps.setInt(6, if (extension.enabled) 1 else 0)
            ps.setInt(7, if (extension.bypassIncomingRequests) 1 else 0)
            ps.setObject(8, extension.lastUsedTimestamp)
            ps.setInt(9, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteById(id: Int): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM EncWebExtensionEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeUpdate() > 0
        }
    }

    fun findAll(): List<EncWebExtension> = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM EncWebExtensionEntity ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }

    fun count(): Int = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM EncWebExtensionEntity").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
    }

    private fun map(rs: ResultSet): EncWebExtension = EncWebExtension(
        rs.getInt("id"),
        rs.getString("webClientId"),
        rs.getString("title"),
        rs.getString("extensionPublicKey"),
        rs.getString("sharedBaseKey"),
        rs.getInt("linked") != 0,
        rs.getInt("enabled") != 0,
        rs.getInt("bypassIncomingRequests") != 0,
        rs.getObject("lastUsedTimestamp")?.let { rs.getLong("lastUsedTimestamp") },
    )
}
