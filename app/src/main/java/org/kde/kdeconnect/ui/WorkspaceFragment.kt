/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.kde.kdeconnect_tp.R

/** Persistent navigation; each destination retains its own fields and scroll position. */
class WorkspaceFragment : Fragment() {
    private var destination = 1
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        destination = state?.getInt("destination", 1) ?: arguments?.getInt("destination", 1) ?: 1
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putInt("destination", destination)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(FragmentContainerView(context).apply { id = R.id.workspace_content }, LinearLayout.LayoutParams(-1, 0, 1f))
        val navigation = BottomNavigationView(context).apply {
            menu.add(0, 1, 0, "Home").setIcon(R.drawable.ic_home_black_24dp)
            menu.add(0, 2, 1, "Inbox").setIcon(android.R.drawable.ic_dialog_email)
            menu.add(0, 3, 2, "Settings").setIcon(android.R.drawable.ic_menu_preferences)
            selectedItemId = destination
            setOnItemSelectedListener { item ->
                show(item.itemId)
                true
            }
        }
        root.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    override fun onViewCreated(view: View, state: Bundle?) { show(destination) }
    private fun show(id: Int) {
        destination = id
        val tag = "destination-$id"
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach { transaction.hide(it).setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED) }
        val existing = childFragmentManager.findFragmentByTag(tag)
        val fragment = existing ?: when(id) {
            2 -> InboxFragment()
            else -> SharingFragment().apply { arguments = Bundle().apply { putInt("page", if(id==3) 3 else 0); putString("peer", this@WorkspaceFragment.arguments?.getString("peer")) } }
        }
        if(existing==null) transaction.add(R.id.workspace_content, fragment, tag) else transaction.show(fragment)
        transaction.setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED).setPrimaryNavigationFragment(fragment).commitNow()
    }
}
