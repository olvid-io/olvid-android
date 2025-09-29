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

package io.olvid.messenger.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    searchText: String = "",
    placeholderText: String = "",
    onSearchTextChanged: (String) -> Unit = {},
    onClearClick: () -> Unit = {},
    selectAllBeacon: Any? = null, // any time selectAllBeacon object changes, the text of the search bar is fully selected
) {

    Box {
        Column(
            modifier = modifier
        ) {
            SearchBarInput(
                searchText = searchText,
                placeholderText = placeholderText,
                onSearchTextChanged = onSearchTextChanged,
                onClearClick = onClearClick,
                selectAllBeacon = selectAllBeacon,
            )
        }
    }
}

@Composable
fun SearchBarInput(
    searchText: String,
    placeholderText: String = "",
    onSearchTextChanged: (String) -> Unit = {},
    onClearClick: () -> Unit = {},
    selectAllBeacon: Any? = null,
    requestFocus: Boolean = false
) {
    var showClearButton by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // this was copied from the BasicTextField implementation --> we want to be able to select all the text when the beacon changes
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = searchText, selection = TextRange(searchText.length))) }
    var latestBeaconValue by remember { mutableStateOf(selectAllBeacon) }

    val textFieldValue = if (latestBeaconValue == selectAllBeacon) {
        textFieldValueState.copy(text = searchText)
    } else {
        latestBeaconValue = selectAllBeacon
        textFieldValueState.copy(text = searchText, selection = TextRange(0, searchText.length))
    }

    SideEffect {
        if (
            textFieldValue.selection != textFieldValueState.selection ||
            textFieldValue.composition != textFieldValueState.composition
        ) {
            textFieldValueState = textFieldValue
        }
    }

    var lastTextValue by remember(searchText) { mutableStateOf(searchText) }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                showClearButton = (focusState.isFocused)
            }
            .focusRequester(focusRequester),
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValueState = newValue

            val stringChanged = lastTextValue != newValue.text
            lastTextValue = newValue.text

            if (stringChanged) {
                onSearchTextChanged(newValue.text)
            }
        },
        placeholder = {
            Spacer(modifier = Modifier.requiredWidth(2.dp))
            Text(
                text = placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.body1.copy(color = colorResource(R.color.darkGrey))
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search_blue),
                contentDescription = stringResource(R.string.menu_action_search)
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = showClearButton,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(onClick = {
                    onClearClick()
                    focusManager.clearFocus()
                }) {
                    Icon(
                        imageVector = Filled.Close,
                        contentDescription = "Close",
                    )
                }

            }
        },
        textStyle = OlvidTypography.body1.copy(color = colorResource(R.color.almostBlack)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorResource(R.color.olvid_gradient_dark),
            unfocusedBorderColor = colorResource(R.color.lightGrey),
            cursorColor = colorResource(R.color.olvid_gradient_light)
        ),
        maxLines = 1,
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController?.hide()
        }),
    )

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        }
    }
}


@Composable
fun ExpandableSearchBar(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    FloatingActionButton(
        modifier = modifier,
        onClick = { expanded = !expanded },
        containerColor = colorResource(R.color.olvid_gradient_dark),
        contentColor = Color.White
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = expanded) {
                Spacer(Modifier.requiredWidth(16.dp))
            }
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.menu_action_search)
            )
            AnimatedVisibility(visible = expanded) {
                TextField(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        cursorColor = colorResource(R.color.olvid_gradient_dark),
                        focusedIndicatorColor = colorResource(R.color.olvid_gradient_dark)
                    ),
                    value = value,
                    onValueChange = onValueChange,
                    trailingIcon = {
                        IconButton(onClick = {
                            expanded = false
                            onValueChange("")
                        }) {
                            Icon(
                                imageVector = Filled.Close,
                                contentDescription = "Close",
                            )
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun ExpandableSearchBarPreview() {
    ExpandableSearchBar()
}

