package de.jepfa.yapm.database.repository

import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.model.encrypted.EncLabel
import java.sql.ResultSet

class LabelRepository(private val db: VaultDatabase) {

    fun insert(label: EncLabel): EncLabel = db.withConnection { c ->
        val sql = "INSERT INTO EncLabelEntity (id, uid, name, description, color) VALUES (?,?,?,?,?)"
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, label.id)
            ps.setObject(2, label.uid?.toString())
            ps.setString(3, label.name.toBase64String())
            ps.setString(4, label.description.toBase64String())
            ps.setObject(5, label.color)
            ps.executeUpdate()
        }
        label.id = db.lastInsertRowId(c)
        label
    }

    fun update(label: EncLabel): Boolean = db.withConnection { c ->
        val id = label.id ?: return@withConnection false
        val sql = "UPDATE EncLabelEntity SET uid=?, name=?, description=?, color=? WHERE id=?"
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, label.uid?.toString())
            ps.setString(2, label.name.toBase64String())
            ps.setString(3, label.description.toBase64String())
            ps.setObject(4, label.color)
            ps.setInt(5, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteById(id: Int): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM EncLabelEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteAll() {
        db.withConnection { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM EncLabelEntity") }
        }
    }

    fun findById(id: Int): EncLabel? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM EncLabelEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    fun findAll(): List<EncLabel> = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM EncLabelEntity ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }

    fun count(): Int = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM EncLabelEntity").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
    }

    private fun map(rs: ResultSet): EncLabel = EncLabel(
        rs.getInt("id"),
        rs.getString("uid"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getObject("color")?.let { rs.getInt("color") },
    )
}
