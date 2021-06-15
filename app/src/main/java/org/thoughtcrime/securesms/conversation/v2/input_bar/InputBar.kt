package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.view_input_bar.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.toDp
import kotlin.math.roundToInt

class InputBar : LinearLayout, InputBarEditTextDelegate {

    private val attachmentsButton by lazy { InputBarButton(context, R.drawable.ic_plus_24) }
    private val microphoneButton by lazy { InputBarButton(context, R.drawable.ic_microphone) }

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        LayoutInflater.from(context).inflate(R.layout.view_input_bar, this)
        attachmentsButtonContainer.addView(attachmentsButton)
        attachmentsButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        attachmentsButton.setOnClickListener {  }
        microphoneButtonContainer.addView(microphoneButton)
        microphoneButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        microphoneButton.setOnClickListener {  }
        inputBarEditText.imeOptions = inputBarEditText.imeOptions or 16777216 // Always use incognito keyboard
        inputBarEditText.delegate = this
    }
    // endregion

    override fun inputBarEditTextHeightChanged(newValue: Int) {
        val vMargin = resources.getDimension(R.dimen.small_spacing).roundToInt()
        val layoutParams = this.layoutParams as RelativeLayout.LayoutParams
        layoutParams.height = newValue + 2 * vMargin
        this.layoutParams = layoutParams
    }
    // endregion
}