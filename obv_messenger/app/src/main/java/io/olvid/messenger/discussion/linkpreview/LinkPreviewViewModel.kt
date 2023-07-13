/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.discussion.linkpreview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.customClasses.StringUtils2
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.entity.Fyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LinkPreviewViewModel : ViewModel() {

    val openGraph = MutableLiveData<OpenGraph?>()
    private var findLinkJob: Job? = null
    private val loaderJobs = HashMap<Long, Job?>()
    private val linkPreviewRepository = LinkPreviewRepository()

    fun findLinkPreview(text: String?, imageWidth: Int, imageHeight: Int) {
        findLinkJob?.safeCancel()
        StringUtils2.getLink(text)?.let { (original, url) ->
            if (url == openGraph.value?.url) return // link already present
            if (url == null) return
            findLinkJob = viewModelScope.launch {
                delay(300) //debounce
                openGraph.value =
                    linkPreviewRepository.fetchOpenGraph(url, imageWidth, imageHeight).apply { originalUrl = original }
            }
        } ifNull {
            reset()
        }
    }

    fun linkPreviewLoader(text: String?, imageWidth: Int, imageHeight: Int, messageId: Long, onSuccess: (OpenGraph) -> Unit) {
        if (loaderJobs[messageId]?.isActive == true) {
            return
        }
        StringUtils2.getLink(text)?.let { (original, url) ->
            if (url == null) return
            loaderJobs[messageId] = viewModelScope.launch {
                onSuccess.invoke(
                    linkPreviewRepository.fetchOpenGraph(url, imageWidth, imageHeight).apply { originalUrl = original })
                loaderJobs.remove(messageId)
            }
        }
    }

    fun linkPreviewLoader(fyle: Fyle, url: String, messageId: Long, onSuccess: (OpenGraph?) -> Unit) {
        if (loaderJobs[messageId]?.isActive == true) {
            return
        }
        loaderJobs[messageId] = viewModelScope.launch {
            onSuccess.invoke(linkPreviewRepository.decodeOpenGraph(fyle).also { it?.url = url })
            loaderJobs.remove(messageId)
        }
    }

    fun clearLinkPreview() {
        openGraph.postValue(OpenGraph(url = openGraph.value?.url))
    }

    fun reset() {
        openGraph.postValue(null)
    }
}

// returns matched text (original url) first and actual link url

fun Job.safeCancel(cause: CancellationException? = null) {
    if (isActive) cancel(cause)
}