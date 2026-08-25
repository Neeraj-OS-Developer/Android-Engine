/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.tom.rv2ide.artificial.agents

import android.content.Context

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

object AIAgentRegistry {
    private val agents = mutableMapOf<String, AgentFactory>()
    
    interface AgentFactory {
        fun create(context: Context): AIAgent
        fun hasValidApiKey(): Boolean
        fun getApiKey(): String?
    }
    
    fun register(providerId: String, factory: AgentFactory) {
        agents[providerId] = factory
    }
    
    fun unregister(providerId: String) {
        agents.remove(providerId)
    }
    
    fun getAgent(providerId: String, context: Context): AIAgent? {
        return agents[providerId]?.create(context)
    }
    
    fun getAllProviderIds(): List<String> {
        return agents.keys.toList()
    }
    
    fun getAvailableProviders(): List<String> {
        return agents.filter { it.value.hasValidApiKey() }.keys.toList()
    }
    
    fun hasProvider(providerId: String): Boolean {
        return agents.containsKey(providerId)
    }
    
    fun getFactory(providerId: String): AgentFactory? {
        return agents[providerId]
    }
}