/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.ui

import com.google.android.material.button.MaterialButton as Button

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.pingora.BridgePlugin
import org.kde.kdeconnect.pingora.LocalInbox

class InboxFragment:Fragment(){
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val context=requireContext();val layout=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;val p=(24*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
        val search=com.google.android.material.textfield.TextInputEditText(context).apply {hint="Search saved notifications";filters=arrayOf(android.text.InputFilter.LengthFilter(1024))};layout.addView(com.google.android.material.textfield.TextInputLayout(context).apply {hint="Search notifications";boxBackgroundMode=com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE;addView(search)})
        val rows=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        fun render(){
            rows.removeAllViews()
            if(!LocalInbox.storageHealthy(context))rows.addView(TextView(context).apply {text="Saved inbox could not be read. Existing storage has been preserved. Clear history to reset it."})
            val items=LocalInbox.search(context,search.text.toString())
            if(items.isEmpty())rows.addView(TextView(context).apply {text="No saved notifications match, or private content is hidden while locked."})
            for(item in items){
                val card=com.google.android.material.card.MaterialCardView(context).apply {
                    radius=20*resources.displayMetrics.density;strokeWidth=0
                    setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(layout,com.google.android.material.R.attr.colorSurfaceContainer))
                    layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=(12*resources.displayMetrics.density).toInt()}
                }
                val itemRows=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;val p=(18*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
                card.addView(itemRows);rows.addView(card)
                itemRows.addView(TextView(context).apply {text="${item.optString("title").ifEmpty {item.optString("sender")}}\n${item.optString("sender").takeIf {it!=item.optString("title")}.orEmpty()}\n${item.optString("app")}";textSize=18f;setPadding(0,24,0,8)})
                val body=TextView(context).apply {text=item.optString("body");maxLines=3;setTextIsSelectable(true)};itemRows.addView(body)
                itemRows.addView(Button(context).apply {isAllCaps=false;strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary)); text="Expand or collapse";setOnClickListener {body.maxLines=if(body.maxLines==3)Int.MAX_VALUE else 3}})
                val code=Regex("(?:otp|verification|passcode|code)\\D{0,30}(\\d{4,8})\\b",RegexOption.IGNORE_CASE).find(item.optString("body"))?.groupValues?.get(1)
                if(code!=null)itemRows.addView(Button(context).apply {isAllCaps=false;strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary)); text="Copy code";setOnClickListener {context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Code",code))}})
                if(item.optBoolean("active"))itemRows.addView(Button(context).apply {isAllCaps=false;strokeWidth=0;backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary)); text="Dismiss source notification";setOnClickListener {
                    val peer=item.optString("peer");LocalInbox.dismiss(context,peer,item.optString("source"),item.optString("version"));KdeConnect.getInstance().devices[peer]?.getPlugin<BridgePlugin>()?.let {LocalInbox.flush(context,peer,it)};render()
                }})
            }
        }
        layout.addView(Button(context).apply {isAllCaps=false; text="Clear saved history (source notifications stay)";setOnClickListener {LocalInbox.clear(context);render()}})
        layout.addView(Button(context).apply {isAllCaps=false; text="Refresh inbox";setOnClickListener {render()}})
        layout.addView(rows);search.addTextChangedListener {render()};render()
        return ScrollView(context).apply {addView(layout)}
    }
}
