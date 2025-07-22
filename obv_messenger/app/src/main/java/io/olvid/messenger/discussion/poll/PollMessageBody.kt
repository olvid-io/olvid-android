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

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonPoll
import io.olvid.messenger.databases.entity.jsons.JsonPollCandidate
import io.olvid.messenger.designsystem.components.CircleCheckBox
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun PollMessageBody(
    pollResults: List<PollVote>,
    jsonPoll: JsonPoll,
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)? = null,
    onVote: (voteUuid: UUID, voted: Boolean) -> Unit = { _, _ -> }
) {
    Column {
        val context = LocalContext.current
        Text(
            text = highlighter?.invoke(context, AnnotatedString(jsonPoll.question))
                ?: AnnotatedString(jsonPoll.question),
            style = OlvidTypography.h2.copy(color = colorResource(R.color.almostBlack))
        )
        if (jsonPoll.multipleChoice) {
            Text(
                stringResource(R.string.explanation_poll_answer_multiple_choice),
                style = OlvidTypography.body2.copy(
                    color = colorResource(
                        R.color.greyTint
                    ), fontStyle = FontStyle.Italic
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Poll answers
        val votesByCandidateUuid = remember(pollResults) {
            pollResults.filter { it.voted }.groupBy { it.voteUuid }
        }
        val totalVoted = remember(pollResults) {
            pollResults.count { it.voted }
        }
        jsonPoll.candidates.forEachIndexed { index, candidate ->
            val candidateVotes = remember(votesByCandidateUuid, candidate.uuid) {
                votesByCandidateUuid[candidate.uuid] ?: emptyList()
            }
            val candidateProgress = remember(candidateVotes, totalVoted) {
                if (totalVoted > 0) candidateVotes.size.toFloat() / totalVoted else 0f
            }
            var progress by remember { mutableFloatStateOf(0f) }
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
            )
            LaunchedEffect(candidateProgress) {
                progress = candidateProgress
            }
            val checked = remember(pollResults) {
                pollResults.any {
                    it.voteUuid == candidate.uuid && it.voter.contentEquals(
                        AppSingleton.getBytesCurrentIdentity()
                    ) && it.voted
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onValueChange = {
                            onVote(candidate.uuid, it)
                        },
                        role = Role.Checkbox
                    )
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    CircleCheckBox(checked = checked, onCheckedChange = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        modifier = Modifier.weight(1f, true),
                        text = with(
                            AnnotatedString(
                                candidate.getText(context)
                            )
                        ) {
                            highlighter?.invoke(
                                context,
                                this
                            ) ?: this
                        },
                        style = OlvidTypography.body2.copy(
                            color = colorResource(R.color.almostBlack)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(candidateProgress * 100).roundToInt()}%",
                        style = OlvidTypography.body2.copy(
                            color = colorResource(R.color.almostBlack)
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp)
                        .height(6.dp),
                    progress = {
                        animatedProgress
                    },
                    trackColor = colorResource(R.color.almostBlack).copy(alpha = 0.33f),
                    color = colorResource(POLL_COLORS[index % POLL_COLORS.size]),
                    gapSize = (-6).dp,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {}
                )
            }
        }
        // expiration
        if (jsonPoll.expiration != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_timer_small),
                    tint = colorResource(R.color.greyTint),
                    contentDescription = null
                )
                var remainingTime by remember {
                    mutableFloatStateOf(
                        (jsonPoll.expiration - System.currentTimeMillis()).toFloat()
                    )
                }
                val coroutineScope = rememberCoroutineScope()
                DisposableEffect(jsonPoll.expiration) {
                    val job = coroutineScope.launch {
                        while (remainingTime > 0) {
                            delay(1000)
                            remainingTime -= 1000
                        }
                    }
                    onDispose {
                        job.cancel()
                    }
                }
                Text(
                    text = if (remainingTime > 0) {
                        stringResource(
                            R.string.label_poll_expiration,
                            StringUtils.getNiceDurationString(
                                context,
                                (remainingTime / 1000).toLong()
                            )
                        )
                    } else {
                        stringResource(R.string.label_poll_ended)
                    },
                    style = OlvidTypography.body2.copy(
                        color = colorResource(
                            R.color.greyTint
                        )
                    )
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
private fun PollMessageBodyPreview() {
    PollMessageBody(
        jsonPoll = dummyJsonPoll(),
        pollResults = emptyList(),
        onVote = { _, _ -> }
    )
}

fun dummyJsonPoll() = JsonPoll().apply {
    question = "What is your favorite color?"
    candidates = listOf(
        JsonPollCandidate().apply {
            text = "Red"
            uuid = UUID.randomUUID()
        },
        JsonPollCandidate().apply {
            text = "Green"
            uuid = UUID.randomUUID()
        },
        JsonPollCandidate().apply {
            text = "Blue"
            uuid = UUID.randomUUID()
        }
    )
    multipleChoice = true
    expiration = System.currentTimeMillis() + 60 * 60 * 1000
}