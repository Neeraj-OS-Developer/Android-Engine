package com.tom.rv2ide.contributors

import com.google.gson.annotations.SerializedName

// Service ki zaroorat nahi hai kyunki hum internet se data nahi le rahe
interface GitHubContributorsService {
  // Isse khaali chhod sakte hain
}

object GitHubContributors {

  /** * Sirf aapki profile dikhane ke liye modified function.
   * Yahan se baaki saare contributors delete ho jayenge.
   */
  suspend fun getAllContributors(): List<GitHubContributor> {
    return listOf(
        GitHubContributor(
            id = 1,
            username = "Neeraj-OS-Developer", // Apna naam yahan likhein
            avatarUrl = "file:///android_asset/icon.png", // Assets wali photo
            profileUrl = "https://github.com/Neeraj-OS-Developer" // Apna Project Link
        )
    )
  }
}

/** A GitHub contributor data class. */
data class GitHubContributor(
    @SerializedName("id") override val id: Int,
    @SerializedName("login") override val username: String,
    @SerializedName("avatar_url") override val avatarUrl: String,
    @SerializedName("html_url") override val profileUrl: String,
) : Contributor
