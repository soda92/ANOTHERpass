package de.jepfa.yapm.database.repository

import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.model.encrypted.EncCredential
import java.sql.ResultSet

class CredentialRepository(private val db: VaultDatabase) {

    fun insert(credential: EncCredential): EncCredential = db.withConnection { c ->
        val sql = """
            INSERT INTO EncCredentialEntity
              (id, uid, name, additionalInfo, user, password, lastPassword, website, labels,
               expiresAt, isObfuscated, isLastPasswordObfuscated, otpData, modifyTimestamp, pinned)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, credential.id)
            ps.setObject(2, credential.uid?.toString())
            ps.setString(3, credential.name.toBase64String())
            ps.setString(4, credential.additionalInfo.toBase64String())
            ps.setString(5, credential.user.toBase64String())
            ps.setString(6, credential.passwordData.password.toBase64String())
            ps.setObject(7, credential.passwordData.lastPassword?.toBase64String())
            ps.setString(8, credential.website.toBase64String())
            ps.setString(9, credential.labels.toBase64String())
            ps.setObject(10, credential.timeData.expiresAt.toBase64String())
            ps.setInt(11, if (credential.passwordData.isObfuscated) 1 else 0)
            ps.setInt(12, if (credential.passwordData.isLastPasswordObfuscated == true) 1 else 0)
            ps.setObject(13, credential.otpData?.encOtpAuthUri?.toBase64String())
            ps.setLong(
                14,
                credential.timeData.modifyTimestamp ?: credential.id?.toLong() ?: 0L,
            )
            ps.setInt(15, if (credential.pinned) 1 else 0)
            ps.executeUpdate()
        }
        credential.id = db.lastInsertRowId(c)
        credential
    }

    fun update(credential: EncCredential): Boolean = db.withConnection { c ->
        val id = credential.id ?: return@withConnection false
        val sql = """
            UPDATE EncCredentialEntity SET
              uid=?, name=?, additionalInfo=?, user=?, password=?, lastPassword=?, website=?,
              labels=?, expiresAt=?, isObfuscated=?, isLastPasswordObfuscated=?, otpData=?,
              modifyTimestamp=?, pinned=?
            WHERE id=?
        """.trimIndent()
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, credential.uid?.toString())
            ps.setString(2, credential.name.toBase64String())
            ps.setString(3, credential.additionalInfo.toBase64String())
            ps.setString(4, credential.user.toBase64String())
            ps.setString(5, credential.passwordData.password.toBase64String())
            ps.setObject(6, credential.passwordData.lastPassword?.toBase64String())
            ps.setString(7, credential.website.toBase64String())
            ps.setString(8, credential.labels.toBase64String())
            ps.setObject(9, credential.timeData.expiresAt.toBase64String())
            ps.setInt(10, if (credential.passwordData.isObfuscated) 1 else 0)
            ps.setInt(11, if (credential.passwordData.isLastPasswordObfuscated == true) 1 else 0)
            ps.setObject(12, credential.otpData?.encOtpAuthUri?.toBase64String())
            ps.setLong(13, credential.timeData.modifyTimestamp ?: 0L)
            ps.setInt(14, if (credential.pinned) 1 else 0)
            ps.setInt(15, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteById(id: Int): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM EncCredentialEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeUpdate() > 0
        }
    }

    fun deleteAll() {
        db.withConnection { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM EncCredentialEntity") }
        }
    }

    fun findById(id: Int): EncCredential? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM EncCredentialEntity WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    fun findByUid(uid: String): EncCredential? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM EncCredentialEntity WHERE uid=?").use { ps ->
            ps.setString(1, uid)
            ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    fun findAll(): List<EncCredential> = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM EncCredentialEntity ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }

    fun count(): Int = db.withConnection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM EncCredentialEntity").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
    }

    private fun map(rs: ResultSet): EncCredential = EncCredential(
        rs.getInt("id"),
        rs.getString("uid"),
        rs.getString("name"),
        rs.getString("additionalInfo"),
        rs.getString("user"),
        rs.getString("password"),
        rs.getString("lastPassword"),
        rs.getString("website"),
        rs.getString("labels"),
        rs.getString("expiresAt"),
        rs.getInt("isObfuscated") != 0,
        if (rs.getObject("isLastPasswordObfuscated") == null) null
        else rs.getInt("isLastPasswordObfuscated") != 0,
        rs.getString("otpData"),
        rs.getLong("modifyTimestamp"),
        rs.getInt("pinned") != 0,
    )
}
