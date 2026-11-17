package de.jepfa.yapm.database.repository

import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.model.encrypted.EncUsernameTemplate
import java.sql.ResultSet

class UsernameTemplateRepository(private val db: VaultDatabase) {

    fun insert(template: EncUsernameTemplate): EncUsernameTemplate = db.withConnection { c ->
        val sql = "INSERT INTO EncUsernameTemplateEntity (id, username, description, generatorType) VALUES (?,?,?,?)"
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, template.id)
            ps.setString(2, template.username.toBase64String())
            ps.setString(3, template.description.toBase64String())
            ps.setString(4, template.generatorType.toBase64String())
            ps.executeUpdate()
        }
        template.id = db.lastInsertRowId(c)
        template
    }

    fun update(template: EncUsernameTemplate): Boolean = db.withConnection { c ->
        val id = template.id ?: return@withConnection false
        val sql = "UPDATE EncUsernameTemplateEntity SET username=?, description=?, generatorType=? WHERE id=?"
        c.prepareStatement(sql).use { ps ->
            ps.setString(1, template.username.toBase64String())
            ps.setString(2, template.description.toBase64String())
            ps.setString(3, template.generatorType.toBase64String())
            ps.setInt(4, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteById(id: Int): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM EncUsernameTemplateEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteAll() {
        db.withConnection { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM EncUsernameTemplateEntity") }
        }
    }

    fun findById(id: Int): EncUsernameTemplate? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM EncUsernameTemplateEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    fun findAll(): List<EncUsernameTemplate> = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM EncUsernameTemplateEntity ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }

    fun count(): Int = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM EncUsernameTemplateEntity").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
    }

    private fun map(rs: ResultSet): EncUsernameTemplate = EncUsernameTemplate(
        rs.getInt("id"),
        rs.getString("username"),
        rs.getString("description"),
        rs.getString("generatorType"),
    )
}
