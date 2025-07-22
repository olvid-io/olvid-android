/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.google_services

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory

class GooglePlay {
    companion object {
        fun launchReviewFlow(activity: Activity) {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    if (reviewInfo != null) {
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener { reviewFlowTask ->
                            if (reviewFlowTask.isSuccessful.not()) {
                                launchPlayStoreFallback(activity)
                            }
                        }
                    } else {
                        launchPlayStoreFallback(activity)
                    }
                } else {
                    launchPlayStoreFallback(activity)
                }
            }
        }
    }
}

private fun launchPlayStoreFallback(activity: Activity) {
    val packageName = activity.packageName
    runCatching {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "market://details?id=$packageName".toUri()
            )
        )
    }.onFailure {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri()
            )
        )
    }
}

