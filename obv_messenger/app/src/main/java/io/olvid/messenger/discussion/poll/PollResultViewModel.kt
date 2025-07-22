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

package io.olvid.messenger.discussion.poll

import android.content.ContentResolver
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonPoll
import io.olvid.messenger.designsystem.components.DonutChart
import io.olvid.messenger.designsystem.components.DonutChartAnimationMode
import io.olvid.messenger.designsystem.components.DonutChartData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val POLL_COLORS = listOf(
    R.color.poll0,
    R.color.poll1,
    R.color.poll2,
    R.color.poll3,
    R.color.poll4,
    R.color.poll5,
    R.color.poll6,
    R.color.poll7,
    R.color.poll8,
    R.color.poll9,
    R.color.poll10,
    R.color.poll11,
//    "#F44336", // Red
//    "#4CAF50", // Green
//    "#2196F3", // Blue
//    "#FFEB3B", // Yellow
//    "#9C27B0", // Purple
//    "#FF9800", // Orange
//    "#03A9F4", // Light Blue
//    "#8BC34A", // Light Green
//    "#E91E63", // Pink
//    "#00BCD4", // Cyan
//    "#CDDC39", // Lime
//    "#673AB7", // Deep Purple
//    "#FFC107", // Amber
//    "#009688", // Teal
//    "#3F51B5"  // Indigo
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ColorsPreview() {
    Surface(
        color = colorResource(R.color.almostWhite)
    ) {
        DonutChart(
            modifier = Modifier.size(250.dp),
            data = POLL_COLORS.map {
                DonutChartData(
                    label = String.format("#%06X", colorResource(it).toArgb() and 0xffffff),
                    percentage = (1 / POLL_COLORS.size.toDouble()) * 100.0,
                    color = colorResource(it)
                )
            },
            animationMode = DonutChartAnimationMode.NEVER
        )
    }
}

class PollResultViewModel(messageId: Long) : ViewModel() {
    private var csvContentToExport: String? = null
    private val db = AppDatabase.getInstance()
    private val messageDao = db.messageDao()
    private val messageRecipientInfoDao = db.messageRecipientInfoDao()

    val message: LiveData<Message?> = messageDao.getLive(messageId)
    val pollResults = message.switchMap { message ->
        message?.let {
            db.pollVoteDao().getAllVotersChoicesOrNone(messageId = message.id)
        }
    }
    // instead of using the group members, we base the pollParticipants on actual MessageRecipientInfo  --> only the sender of the poll can see who is still expected to answer
    val pollParticipants: LiveData<List<Contact>> = message.switchMap { message ->
        message?.let {
            AppSingleton.getBytesCurrentIdentity()?.let {
                messageRecipientInfoDao.getAllRecipientContactsForMessage(message.id, it)
            }
        }  ?: MutableLiveData(emptyList())
    }

    fun generateCsvDataForExport(
        poll: JsonPoll,
        pollVotes: List<PollVote>,
    ): String {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvData = StringBuilder()
        csvData.append(App.getContext().getString(R.string.poll_csv_headers)).append("\r\n")
        val pollQuestionSanitized = poll.question.replace("\"", "\"\"")
        val candidatesMap = poll.candidates.associateBy { it.uuid }

        pollVotes.forEach { vote ->
            val voterName = ContactCacheSingleton.getContactCustomDisplayName(vote.voter) ?: "?"
            val voterNameSanitized = voterName.replace("\"", "\"\"")
            val candidateText = candidatesMap[vote.voteUuid]?.text?.replace("\"", "\"\"") ?: "?"
            val timestamp = simpleDateFormat.format(Date(vote.serverTimestamp))
            csvData.append("\"$pollQuestionSanitized\",\"$voterNameSanitized\",\"$candidateText\",\"$timestamp\"\r\n")
        }

        csvContentToExport = csvData.toString() // Store CSV content

        return "${App.getContext().getString(R.string.text_poll)}_${poll.question.take(20).replace("\\s+".toRegex(), "_")}.csv"
    }

    fun exportPollCsv(uri: Uri?, contentResolver: ContentResolver) {
        runCatching {
            if (uri != null && csvContentToExport != null) {
                if (StringUtils.validateUri(uri)) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(
                                csvContentToExport.orEmpty().toByteArray()
                            )
                        }
                    }.onSuccess {
                        App.toast(
                            R.string.toast_message_success_exporting_poll_csv,
                            Toast.LENGTH_SHORT
                        )
                    }.onFailure {
                        App.toast(
                            R.string.toast_message_error_exporting_poll_csv,
                            Toast.LENGTH_SHORT
                        )
                    }.also {
                        csvContentToExport = null
                    }
                }
            }
        }
    }
}