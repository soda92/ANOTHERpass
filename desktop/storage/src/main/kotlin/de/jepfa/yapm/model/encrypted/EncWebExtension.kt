package de.jepfa.yapm.model.encrypted

data class EncWebExtension(
    var id: Int?,
    var webClientId: Encrypted,
    var title: Encrypted,
    var extensionPublicKey: Encrypted,
    var sharedBaseKey: Encrypted,
    var linked: Boolean,
    var enabled: Boolean,
    var bypassIncomingRequests: Boolean,
    var lastUsedTimestamp: Long?,
) {

    constructor(
        id: Int?,
        webClientIdBase64: String,
        titleBase64: String,
        extensionPublicKeyBase64: String,
        sharedBaseKeyBase64: String,
        linked: Boolean,
        enabled: Boolean,
        bypassIncomingRequests: Boolean,
        lastUsedTimestamp: Long?,
    ) : this(
        id,
        Encrypted.fromBase64String(webClientIdBase64),
        Encrypted.fromBase64String(titleBase64),
        Encrypted.fromBase64String(extensionPublicKeyBase64),
        Encrypted.fromBase64String(sharedBaseKeyBase64),
        linked,
        enabled,
        bypassIncomingRequests,
        lastUsedTimestamp,
    )

    fun isPersistent(): Boolean = id != null

    fun touch() {
        lastUsedTimestamp = System.currentTimeMillis()
    }
}
