package com.turbostevek.ksefimporter.ksef

/** Token KSeF from the secret store. Its value is never printed. */
@JvmInline
value class KsefToken(val value: String) {
    override fun toString(): String = "KsefToken(***)"
}

/** Session access token returned by KSeF after login. Its value is never printed. */
@JvmInline
value class AccessToken(val value: String) {
    override fun toString(): String = "AccessToken(***)"
}
