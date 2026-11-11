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
        val search=EditText(context).apply {hint="Search saved notifications";filters=arrayOf(android.text.InputFilter.LengthFilter(1024))};layout.addView(search)
        val rows=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        fun render(){
            rows.removeAllViews()
            if(!LocalInbox.storageHealthy(context))rows.addView(TextView(context).apply {text="Saved inbox could not be read. Existing storage has been preserved. Clear history to reset it."})
            val items=LocalInbox.search(context,search.text.toString())
            if(items.isEmpty())rows.addView(TextView(context).apply {text="No saved notifications match, or private content is hidden while locked."})
            for(item in items){
                rows.addView(TextView(context).apply {text="${item.optString("sender").ifEmpty {item.optString("title")}}\n${item.optString("app")}";textSize=18f;setPadding(0,24,0,8)})
                val body=TextView(context).apply {text=item.optString("body");maxLines=3;setTextIsSelectable(true)};rows.addView(body)
                rows.addView(Button(context).apply {isAllCaps=false; text="Expand or collapse";setOnClickListener {body.maxLines=if(body.maxLines==3)Int.MAX_VALUE else 3}})
                val code=Regex("(?:otp|verification|passcode|code)\\D{0,30}(\\d{4,8})\\b",RegexOption.IGNORE_CASE).find(item.optString("body"))?.groupValues?.get(1)
                if(code!=null)rows.addView(Button(context).apply {isAllCaps=false; text="Copy code";setOnClickListener {context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Code",code))}})
                if(item.optBoolean("active"))rows.addView(Button(context).apply {isAllCaps=false; text="Dismiss source notification";setOnClickListener {
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
