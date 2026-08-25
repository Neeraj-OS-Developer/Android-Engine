package com.tom.rv2ide.artificial.services

// DEPRECATED //

/*
 ** @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

interface ArtificialService {
  suspend fun generateCode(
      prompt: String,
      context: String? = null,
      language: String = "any",
      projectStructure: String? = null,
  ): Result<String>
}
