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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonPoll
import io.olvid.messenger.designsystem.components.AccessibilityFakeButton
import io.olvid.messenger.designsystem.components.DonutChart
import io.olvid.messenger.designsystem.components.DonutChartData
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.ContactListItem
import java.util.UUID

@Composable
fun PollResultScreen(
    poll: JsonPoll,
    pollParticipants: List<Contact>,
    pollResults: List<PollVote>,
    onBackPressed: () -> Unit,
    onExportCsv: () -> Unit
) {
    var selectedPollVote: UUID? by rememberSaveable { mutableStateOf(null) }
    fun handleBack() = when {
        selectedPollVote != null -> selectedPollVote = null
        else -> onBackPressed()
    }

    BackHandler {
        handleBack()
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = colorResource(R.color.almostWhite),
        contentColor = colorResource(R.color.almostBlack),
        topBar = {
            OlvidTopAppBar(
                titleText = selectedPollVote?.let { selectedVote -> poll.candidates.find { it.uuid == selectedVote } }?.text
                    ?: stringResource(R.string.label_poll_answers),
                onBackPressed = ::handleBack
            )
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            var firstEntry by remember { mutableStateOf(true) }
            AnimatedVisibility(
                visible = selectedPollVote == null,
                enter = if (firstEntry) EnterTransition.None else slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                PollOverview(
                    modifier = Modifier.padding(top = contentPadding.calculateTopPadding())
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    onBackPressed = ::handleBack,
                    poll = poll,
                    pollParticipants = pollParticipants,
                    pollResults = pollResults,
                    onCandidateClick = { candidateUuid ->
                        selectedPollVote = candidateUuid
                    },
                    onExportCsv = onExportCsv
                )
            }
            AnimatedVisibility(
                visible = selectedPollVote != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                firstEntry = false
                val lastSelected by remember { mutableStateOf(selectedPollVote ?: UUID(0, 0)) }
                SelectedAnswerVotersList(
                    modifier = Modifier.padding(top = contentPadding.calculateTopPadding())
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    onBackPressed = ::handleBack,
                    selectedVoteUuid = lastSelected,
                    pollResults = pollResults,
                )
            }
        }
    }
}

@Composable
fun SelectedAnswerVotersList(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    selectedVoteUuid: UUID,
    pollResults: List<PollVote>,
) {
    val context = LocalContext.current
    val pollVotes = pollResults.filter { it.voteUuid == selectedVoteUuid && it.voted }
        .sortedByDescending { it.serverTimestamp }
    val answers = remember(pollResults) {
        pollResults.count { it.voted && it.voteUuid == selectedVoteUuid }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = stringResource(R.string.label_poll_answers) + " ($answers)",
            style = OlvidTypography.body1.copy(
                fontWeight = FontWeight.Medium,
                color = colorResource(R.color.almostBlack)
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lighterGrey)),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
        ) {
            items(
                items = pollVotes,
                key = { pollVote -> pollVote.voter.contentToString() }) { pollVote ->
                ContactListItem(
                    modifier = Modifier
                        .background(colorResource(id = R.color.almostWhite)),
                    padding = PaddingValues(horizontal = 12.dp),
                    title = AnnotatedString(
                        ContactCacheSingleton.getContactDetailsFirstLine(pollVote.voter)
                            ?: stringResource(R.string.text_deleted_contact)
                    ),
                    body = ContactCacheSingleton.getContactDetailsSecondLine(pollVote.voter)?.let {
                        AnnotatedString(it)
                    },
                    initialViewSetup = { it.setFromCache(pollVote.voter) },
                    endContent = {
                        Text(
                            text = StringUtils.getNiceDateString(
                                context,
                                pollVote.serverTimestamp
                            ).toString()
                        )
                    },
                    onClick = {})
                if (pollVote != pollVotes.lastOrNull()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = colorResource(R.color.lightGrey)
                    )
                }
            }
            item {
                AccessibilityFakeButton(
                    modifier = Modifier.height(16.dp),
                    text = stringResource(R.string.content_description_back_button),
                    onClick = onBackPressed
                )
            }
        }
    }
}

@Composable
fun PollOverview(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    poll: JsonPoll,
    pollParticipants: List<Contact>,
    pollResults: List<PollVote>,
    onCandidateClick: (UUID) -> Unit,
    onExportCsv: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .verticalScroll(rememberScrollState())
    ) {
        val votedPollResults by remember(pollResults) {
            mutableStateOf(pollResults.filter { it.voted })
        }
        val totalVoted by remember(votedPollResults) {
            mutableIntStateOf(votedPollResults.size)
        }
        val donutData = remember(pollResults) {
            buildList {
                poll.candidates.forEachIndexed { index, candidate ->
                    val candidateVotes =
                        pollResults.filter { it.voteUuid == candidate.uuid && it.voted }
                    val candidateProgress =
                        if (totalVoted > 0) candidateVotes.size.toFloat() / totalVoted else 0f

                    if (candidateProgress > 0) {
                        add(
                            DonutChartData(
                                label = candidate.getText(context),
                                percentage = candidateProgress * 100.0,
                                color = Color(ContextCompat.getColor(context, POLL_COLORS[index % POLL_COLORS.size]))
                            )
                        )
                    }
                }
            }
        }
        AnimatedVisibility(donutData.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                DonutChart(
                    modifier = Modifier.size(250.dp),
                    data = donutData,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                text = poll.question,
                style = OlvidTypography.body1.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }
        val answersCount = remember(pollResults) {
            pollResults.count { it.voted }
        }

        var sortByPollOrder by rememberSaveable { mutableStateOf(false) }
        var showDropdown by remember { mutableStateOf(false) }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 20.dp),
                text = stringResource(R.string.label_poll_answers) + " ($answersCount)",
                style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple()
                    ) { showDropdown = !showDropdown }
                    .padding(4.dp),
                verticalAlignment = CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_poll_answers_sorted_by),
                    style = OlvidTypography.body2.copy(color = colorResource(R.color.almostBlack))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevrons_sort),
                    tint = colorResource(R.color.almostBlack),
                    contentDescription = stringResource(R.string.label_poll_answers_sorted_by)
                )
                OlvidDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.label_poll_answers_sorted_by_poll_order),
                        onClick = {
                            sortByPollOrder = true
                            showDropdown = false
                        },
                        trailingIcon = {
                            if (sortByPollOrder) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.label_poll_answers_sorted_by_answer_count),
                        onClick = {
                            sortByPollOrder = false
                            showDropdown = false
                        },
                        trailingIcon = {
                            if (!sortByPollOrder) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SectionCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (answersCount == 0) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_poll_no_votes_yet),
                        style = OlvidTypography.body2.copy(
                            fontStyle = FontStyle.Italic
                        )
                    )
                }
            } else {
                val answers by remember(poll.candidates, sortByPollOrder) {
                    mutableStateOf(
                        if (sortByPollOrder) poll.candidates else poll.candidates.sortedByDescending { candidate ->
                            pollResults.count { it.voteUuid == candidate.uuid && it.voted }
                        }
                    )
                }
                answers.forEachIndexed { index, pollAnswer ->
                    key(pollAnswer.uuid) {
                        val votes =
                            remember(pollResults) { pollResults.count { it.voteUuid == pollAnswer.uuid && it.voted } }
                        if (votes > 0) {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = colorResource(R.color.lightGrey)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple()
                                    ) {
                                        onCandidateClick.invoke(pollAnswer.uuid)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 16.dp),
                                verticalAlignment = CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, true),
                                    verticalAlignment = CenterVertically,
                                ) {
                                    Text(
                                        modifier = Modifier.weight(1f, false).padding(end = 8.dp),
                                        text = pollAnswer.getText(context),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = OlvidTypography.body2
                                    )
                                    val myVote = remember(pollResults) {
                                        pollResults.firstOrNull {
                                            it.voteUuid == pollAnswer.uuid
                                                    && it.voted
                                                    && it.voter.contentEquals(
                                                runCatching { AppSingleton.getBytesCurrentIdentity() }.getOrNull()
                                            )
                                        }
                                    }
                                    myVote?.let { voter ->
                                        InitialView(
                                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                                            initialViewSetup = {
                                                it.setFromCache(voter.voter)
                                            })
                                    }
                                }
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.label_poll_answers,
                                        votes,
                                        votes
                                    ),
                                    color = colorResource(R.color.greyTint)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    tint = colorResource(R.color.greyTint),
                                    contentDescription = stringResource(R.string.button_label_see_details)
                                )
                            }
                        }
                    }
                }
            }
        }

        // CSV export
        if (answersCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.semantics { heading() },
                    onClick = onExportCsv,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorResource(R.color.olvid_gradient_light)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.label_poll_export_csv)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = stringResource(R.string.label_poll_export_csv),
                            style = OlvidTypography.body1
                        )
                    }
                }
            }
        }

        // Pending voters
        if (pollParticipants.isNotEmpty()) {
            val voters = remember(votedPollResults) {
                votedPollResults.distinctBy { it.voter.contentToString() }
                    .map { it.voter.contentToString() }
            }
            val pendingVoters = remember(votedPollResults) {
                pollParticipants.filterNot {
                    voters.contains(it.bytesContactIdentity.contentToString())
                }
            }
            if (pendingVoters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    text = stringResource(R.string.label_poll_answer_pending) + " (${pendingVoters.size})",
                    style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    pendingVoters.forEach { pollVoter ->
                        key(pollVoter.bytesContactIdentity) {
                            ContactListItem(
                                title = AnnotatedString(
                                    ContactCacheSingleton.getContactDetailsFirstLine(pollVoter.bytesContactIdentity)
                                        ?: stringResource(R.string.text_deleted_contact)
                                ),
                                body = ContactCacheSingleton.getContactDetailsSecondLine(pollVoter.bytesContactIdentity)?.let {
                                    AnnotatedString(it)
                                },
                                initialViewSetup = { initialView ->
                                    runCatching {
                                        initialView.setFromCache(pollVoter.bytesContactIdentity)
                                    }.onFailure { initialView.setUnknown() }
                                }, onClick = {})
                            if (pollVoter != pendingVoters.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = colorResource(R.color.lightGrey)
                                )
                            }
                        }
                    }
                }
            }
        }
        AccessibilityFakeButton(
            modifier = Modifier.height(16.dp),
            text = stringResource(R.string.content_description_back_button),
            onClick = onBackPressed
        )
        Spacer(
           modifier = Modifier
               .windowInsetsBottomHeight(WindowInsets.safeDrawing)
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.semantics { heading() },
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.lighterGrey),
            contentColor = colorResource(R.color.almostBlack)
        )
    ) {
        content()
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PollResultScreenPreview() {
    val poll = dummyJsonPoll()
    PollResultScreen(
        poll = poll,
        pollParticipants = emptyList(),
        pollResults = dummyPollResults(poll),
        onBackPressed = {},
        onExportCsv = {})
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PollOverviewPreview() {
    val poll = dummyJsonPoll()
    PollOverview(
        poll = poll,
        pollParticipants = emptyList(),
        pollResults = dummyPollResults(poll),
        onCandidateClick = {},
        onExportCsv = {})
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SelectedAnswerVotersListPreview() {
    val poll = dummyJsonPoll()
    SelectedAnswerVotersList(
        selectedVoteUuid = poll.candidates.first().uuid,
        pollResults = dummyPollResults(poll)
    )
}

fun dummyPollResults(poll: JsonPoll): List<PollVote> = listOf(
    PollVote(
        messageId = 1,
        serverTimestamp = System.currentTimeMillis(),
        version = 0,
        voteUuid = poll.candidates.first().uuid,
        voter = byteArrayOf(0, 1, 35),
        voted = true,
    ),
    PollVote(
        messageId = 2,
        serverTimestamp = System.currentTimeMillis(),
        version = 0,
        voteUuid = poll.candidates.first().uuid,
        voter = byteArrayOf(0, 1, 35),
        voted = false,
    ),
)