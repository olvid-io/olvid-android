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
package io.olvid.messenger.plus_button

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.EmptyRecyclerView
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider
import io.olvid.messenger.customClasses.SearchHighlightSpan
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.StringUtils2.Companion.computeHighlightRanges
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.plus_button.KeycloakSearchFragment.SearchResultAdapter.SearchResultViewHolder
import io.olvid.messenger.plus_button.PlusButtonViewModel.SEARCH_STATUS
import io.olvid.messenger.plus_button.PlusButtonViewModel.SEARCH_STATUS.DONE
import io.olvid.messenger.plus_button.PlusButtonViewModel.SEARCH_STATUS.NONE
import io.olvid.messenger.plus_button.PlusButtonViewModel.SEARCH_STATUS.SEARCHING
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.uppercaseLastName
import java.util.regex.Pattern

class KeycloakSearchFragment : Fragment(), OnClickListener {
    private lateinit var activity: AppCompatActivity
    private val viewModel: PlusButtonViewModel by activityViewModels()

    private var searchResultAdapter: SearchResultAdapter? = null
    private var keycloakSearchEditText: EditText? = null
    private var explanationTextView: TextView? = null
    private var spinner: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plus_button_keycloak_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.top_bar)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updatePadding(top = insets.top)
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = view.context.resources.getDimensionPixelSize(R.dimen.tab_bar_size) + insets.top
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        keycloakSearchEditText = view.findViewById(R.id.keycloak_search_edit_text)
        val searchResultRecyclerView =
            view.findViewById<EmptyRecyclerView>(R.id.keycloak_search_recycler_view)
        val explanationCard = view.findViewById<CardView>(R.id.keycloak_search_explanation)
        explanationTextView = view.findViewById(R.id.keycloak_search_explanation_text)
        spinner = view.findViewById(R.id.keycloak_search_spinner)

        view.findViewById<View>(R.id.search_button).setOnClickListener(this)
        view.findViewById<View>(R.id.back_button).setOnClickListener(this)

        val ownedIdentity = viewModel.currentIdentity
        searchResultAdapter = if (ownedIdentity == null) {
            SearchResultAdapter(null) { userDetails: JsonKeycloakUserDetails ->
                this.onUserClick(userDetails)
            }
        } else {
            SearchResultAdapter(ownedIdentity.bytesOwnedIdentity) { userDetails: JsonKeycloakUserDetails ->
                this.onUserClick(userDetails)
            }
        }
        viewModel.getKeycloakSearchResult().observe(
            viewLifecycleOwner,
            searchResultAdapter!!
        )
        viewModel.getKeycloakSearchMissingResults().observe(
            viewLifecycleOwner
        ) { missingResults: Int? ->
            searchResultAdapter!!.onMissingResultsChanged(
                missingResults
            )
        }

        searchResultRecyclerView.adapter = searchResultAdapter
        searchResultRecyclerView.setEmptyView(explanationCard)
        searchResultRecyclerView.layoutManager = LinearLayoutManager(context)
        searchResultRecyclerView.addItemDecoration(ItemDecorationSimpleDivider(activity, 60, 12))

        keycloakSearchEditText?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            startSearch()
            true
        }
        keycloakSearchEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.keycloakSearchString = s.toString()
            }
        })
        keycloakSearchEditText?.setText(viewModel.keycloakSearchString)

        viewModel.getKeycloakSearchStatus().observe(
            viewLifecycleOwner
        ) { status: SEARCH_STATUS ->
            when (status) {
                NONE -> {
                    explanationTextView?.setGravity(Gravity.START)
                    explanationTextView?.setText(R.string.explanation_keycloak_search_not_started)
                    spinner?.visibility = View.GONE
                    keycloakSearchEditText?.requestFocus()
                }

                SEARCHING -> {
                    explanationTextView?.setGravity(Gravity.CENTER_HORIZONTAL)
                    explanationTextView?.setText(R.string.explanation_keycloak_searching)
                    spinner?.visibility = View.VISIBLE
                }

                DONE -> {
                    explanationTextView?.setGravity(Gravity.CENTER_HORIZONTAL)
                    explanationTextView?.setText(R.string.explanation_empty_keycloak_search)
                    spinner?.visibility = View.GONE
                }
            }
        }
        startSearch()
    }

    private fun startSearch() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(keycloakSearchEditText?.windowToken, 0)

        val ownedIdentity = viewModel.currentIdentity
        if (ownedIdentity != null) {
            viewModel.setKeycloakSearchStatus(SEARCHING)
            viewModel.setKeycloakSearchResult(emptyList())


            searchResultAdapter?.setFilter(viewModel.keycloakSearchString)

            KeycloakManager.getInstance().search(
                ownedIdentity.bytesOwnedIdentity,
                viewModel.keycloakSearchString,
                object : KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Int>> {
                    override fun success(searchResult: Pair<List<JsonKeycloakUserDetails>, Int>) {
                        viewModel.setKeycloakSearchStatus(DONE)
                        viewModel.setKeycloakSearchResult(searchResult.first)
                        if (searchResult.second == null) {
                            viewModel.setKeycloakSearchMissingResults(0)
                        } else {
                            if (searchResult.first == null) {
                                viewModel.setKeycloakSearchMissingResults(searchResult.second!!)
                            } else {
                                viewModel.setKeycloakSearchMissingResults(searchResult.second!! - searchResult.first!!.size)
                            }
                        }
                    }

                    override fun failed(rfc: Int) {
                        viewModel.setKeycloakSearchStatus(DONE)
                        App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                    }
                })
        }
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.back_button) {
            activity.onBackPressed()
        } else if (id == R.id.search_button) {
            startSearch()
        }
    }

    fun onUserClick(userDetails: JsonKeycloakUserDetails) {
        val ownedIdentity = viewModel.currentIdentity ?: return

        var name = ""
        if (userDetails.firstName != null) {
            name += userDetails.firstName
        }
        if (userDetails.lastName != null) {
            if (name.isNotEmpty()) {
                name += " "
            }
            name += userDetails.lastName
        }

        val finalName = name

        val builder: Builder = SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
        builder.setTitle(R.string.dialog_title_add_keycloak_user)
            .setMessage(getString(R.string.dialog_message_add_keycloak_user, finalName))
            .setNegativeButton(R.string.button_label_cancel, null)
            .setPositiveButton(
                R.string.button_label_add_contact
            ) { dialog: DialogInterface?, which: Int ->
                KeycloakManager.getInstance().addContact(
                    ownedIdentity.bytesOwnedIdentity,
                    userDetails.id,
                    userDetails.identity,
                    object : KeycloakCallback<Void?> {
                        override fun success(result: Void?) {
                            App.toast(
                                getString(
                                    R.string.toast_message_contact_added,
                                    finalName
                                ), Toast.LENGTH_SHORT, Gravity.BOTTOM
                            )
                        }

                        override fun failed(rfc: Int) {
                            App.toast(
                                R.string.toast_message_error_retry,
                                Toast.LENGTH_SHORT
                            )
                        }
                    })
            }
        builder.create().show()
    }


    internal class SearchResultAdapter(
        private val bytesOwnedIdentity: ByteArray?,
        private val onClick: (userDetails: JsonKeycloakUserDetails) -> Unit
    ) :
        Adapter<SearchResultViewHolder>(),
        Observer<List<JsonKeycloakUserDetails>?> {
        private var searchResults: List<JsonKeycloakUserDetails>? = null
        private var missingResults = 0
        private val highlightedSpans =
            arrayOfNulls<SearchHighlightSpan>(10)

        private val patterns: MutableList<Pattern> =
            ArrayList()

        init {
            for (i in highlightedSpans.indices) {
                highlightedSpans[i] = SearchHighlightSpan(App.getContext())
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if ((searchResults == null && position == 0) || (searchResults != null && searchResults!!.size == position)) {
                VIEW_TYPE_MISSING_RESULTS
            } else {
                VIEW_TYPE_NORMAL
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
            if (viewType == VIEW_TYPE_MISSING_RESULTS) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_keycloak_missing_count, parent, false)
                return SearchResultViewHolder(view) {}
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_keycloak_user, parent, false)
                return SearchResultViewHolder(view, onClick)
            }
        }

        override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
            if (searchResults != null && position < searchResults!!.size) {
                val userDetails = searchResults!![position]

                holder.userDetails = userDetails

                val identityDetails = userDetails.getIdentityDetails(null)
                val name = identityDetails.formatFirstAndLastName(
                    contactDisplayNameFormat,
                    uppercaseLastName
                )
                matchAndHighlight(name, holder.keycloakUserNameTextView)

                if (userDetails.identity != null && ContactCacheSingleton.getContactCustomDisplayName(
                        userDetails.identity
                    ) != null
                ) {
                    holder.initialView?.setFromCache(userDetails.identity)
                } else {
                    holder.initialView?.reset()
                    holder.initialView?.setInitial(
                        userDetails.identity ?: ByteArray(0),
                        StringUtils.getInitial(name)
                    )
                }

                val posComp = identityDetails.formatPositionAndCompany(contactDisplayNameFormat)
                if (posComp != null) {
                    holder.keycloakUserNameTextView?.maxLines = 1
                    holder.keycloakUserPositionTextView?.visibility = View.VISIBLE
                    matchAndHighlight(posComp, holder.keycloakUserPositionTextView)
                } else {
                    holder.keycloakUserNameTextView?.maxLines = 2
                    holder.keycloakUserPositionTextView?.visibility = View.GONE
                }

                holder.keycloakUserKnownImageView?.visibility = View.GONE
                if (userDetails.identity != null) {
                    if (bytesOwnedIdentity != null) {
                        App.runThread {
                            val contact = AppDatabase.getInstance().contactDao()[bytesOwnedIdentity, userDetails.identity]
                            Handler(Looper.getMainLooper()).post {
                                if (holder.userDetails?.identity.contentEquals(contact?.bytesContactIdentity)) {
                                    if (contact != null) {
                                        holder.initialView?.setContact(contact)
                                        holder.keycloakUserKnownImageView?.visibility =
                                            if (contact.oneToOne) View.VISIBLE else View.GONE
                                    } else {
                                        holder.keycloakUserKnownImageView?.visibility =
                                            View.GONE
                                    }
                                }
                            }
                        }
                    }
                }
            } else if ((searchResults == null && position == 0) || (searchResults != null && searchResults!!.size == position)) {
                holder.missingCountTextView?.text =
                    App.getContext().resources.getQuantityString(
                        R.plurals.text_keycloak_missing_search_result,
                        missingResults,
                        missingResults
                    )
            }
        }

        private fun matchAndHighlight(text: String, textView: TextView?) {
            val regexes: MutableList<Regex> = ArrayList(patterns.size)
            for (pattern in patterns) {
                regexes.add(Regex(pattern.toString()))
            }
            val ranges = computeHighlightRanges(text, regexes)
            var i = 0
            val highlightedString: Spannable = SpannableString(text)
            for ((first, second) in ranges) {
                if (i == highlightedSpans.size) {
                    break
                }
                highlightedString.setSpan(
                    highlightedSpans[i],
                    first,
                    second,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                i++
            }
            textView?.text = highlightedString
        }


        override fun getItemCount(): Int {
            if (searchResults != null) {
                return searchResults!!.size + (if ((missingResults == 0)) 0 else 1)
            }
            return (if ((missingResults == 0)) 0 else 1)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onChanged(value: List<JsonKeycloakUserDetails>?) {
            if (value != null) {
                val filteredResults: MutableList<JsonKeycloakUserDetails> = ArrayList()
                for (userDetail in value) {
                    if (userDetail.identity != null && !bytesOwnedIdentity.contentEquals(userDetail.identity)) {
                        filteredResults.add(userDetail)
                    }
                }
                this.searchResults = filteredResults
            } else {
                this.searchResults = null
            }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun onMissingResultsChanged(missingResults: Int?) {
            if (missingResults == null) {
                this.missingResults = 0
            } else {
                this.missingResults = missingResults
            }
            notifyDataSetChanged()
        }

        fun setFilter(keycloakSearchString: String?) {
            patterns.clear()
            if (keycloakSearchString == null) {
                return
            }
            for (part in keycloakSearchString.trim { it <= ' ' }.split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                patterns.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(part))))
            }
        }

        internal class SearchResultViewHolder(itemView: View, onClick: (userDetails: JsonKeycloakUserDetails) -> Unit) :
            ViewHolder(itemView) {
            val keycloakUserNameTextView: TextView? = itemView.findViewById(R.id.keycloak_user_name)
            val keycloakUserPositionTextView: TextView? =
                itemView.findViewById(R.id.keycloak_user_position)
            val initialView: InitialView? = itemView.findViewById(R.id.initial_view)
            val keycloakUserKnownImageView: ImageView? =
                itemView.findViewById(R.id.keycloak_user_known_image_view)
            val missingCountTextView: TextView? = itemView.findViewById(R.id.keycloak_missing_count)

            var userDetails: JsonKeycloakUserDetails? = null

            init {
                    itemView.setOnClickListener { v: View? ->
                        userDetails?.let {
                            onClick(
                                it
                            )
                        }
                    }
            }
        }

        companion object {
            const val VIEW_TYPE_NORMAL: Int = 0
            const val VIEW_TYPE_MISSING_RESULTS: Int = 1
        }
    }
}
