package de.jepfa.yapm.model.secret

import de.jepfa.yapm.model.Clearable
import de.jepfa.yapm.model.Validable
import java.util.Arrays

open class Secret(var data: ByteArray) : Clearable, Validable {

    open fun isEmpty(): Boolean = data.isEmpty()

    fun isEqual(other: Secret): Boolean = Arrays.equals(data, other.data)

    override fun isValid(): Boolean = !Arrays.equals(toByteArray(), Validable.FAILED_BYTE_ARRAY)

    fun add(other: Secret) {
        val buffer = data + other.data
        clear()
        other.clear()
        data = buffer
    }

    fun toByteArray(): ByteArray = data

    override fun clear() {
        data.fill(0, 0, data.size)
        data = ByteArray(0)
    }
}
