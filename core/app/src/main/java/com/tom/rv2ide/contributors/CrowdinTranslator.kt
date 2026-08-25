package com.tom.rv2ide.contributors

import com.google.gson.annotations.SerializedName

object CrowdinTranslators {

  /** * Isko emptyList() dene se niche wala section screen se hat jayega 
   * ya usme koi photo nahi dikhegi.
   */
  suspend fun getAllTranslators(): List<CrowdinTranslator> {
    return emptyList() // Saare translators yahan se delete ho gaye
  }
}

data class CrowdinTranslator(
    @SerializedName("id") private val _id: String,
    @SerializedName("username") override val username: String,
    @SerializedName("picture") override val avatarUrl: String,
) : Contributor {
  override val id: Int get() = _id.toInt()
  override val profileUrl: String get() = ""
}
