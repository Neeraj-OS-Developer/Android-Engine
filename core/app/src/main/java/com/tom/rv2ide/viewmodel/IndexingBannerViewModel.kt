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

package com.tom.rv2ide.indexing.viewmodels

import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for IndexingBanner to support data binding
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */
class IndexingBannerViewModel : ViewModel() {

    private val _title = MutableLiveData<String>("Indexing project")
    val title: LiveData<String> = _title

    private val _message = MutableLiveData<String>("Preparing...")
    val message: LiveData<String> = _message

    private val _isVisible = MutableLiveData<Boolean>(false)
    val isVisible: LiveData<Boolean> = _isVisible

    private val _iconResId = MutableLiveData<Int?>(null)
    val iconResId: LiveData<Int?> = _iconResId

    /**
     * Update the subtitle message
     */
    fun updateMessage(newMessage: String) {
        _message.value = newMessage
    }

    /**
     * Update the title text
     */
    fun updateTitle(newTitle: String) {
        _title.value = newTitle
    }

    /**
     * Update the icon resource
     */
    fun updateIcon(@DrawableRes iconResId: Int?) {
        _iconResId.value = iconResId
    }

    /**
     * Show the banner with optional parameters
     */
    fun show(
        title: String? = null,
        message: String? = null,
        @DrawableRes iconResId: Int? = null
    ) {
        title?.let { _title.value = it }
        message?.let { _message.value = it }
        iconResId?.let { _iconResId.value = it }
        _isVisible.value = true
    }

    /**
     * Show the banner with default values
     */
    fun show() {
        _isVisible.value = true
    }

    /**
     * Hide the banner
     */
    fun hide() {
        _isVisible.value = false
    }
}