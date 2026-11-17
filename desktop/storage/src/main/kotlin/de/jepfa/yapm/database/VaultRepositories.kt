package de.jepfa.yapm.database

import de.jepfa.yapm.database.repository.CredentialRepository
import de.jepfa.yapm.database.repository.LabelRepository
import de.jepfa.yapm.database.repository.UsernameTemplateRepository
import de.jepfa.yapm.database.repository.WebExtensionRepository

/** Bundles the repositories that share one database connection. */
class VaultRepositories(val db: VaultDatabase) {
    val credentials = CredentialRepository(db)
    val labels = LabelRepository(db)
    val usernameTemplates = UsernameTemplateRepository(db)
    val webExtensions = WebExtensionRepository(db)
}
