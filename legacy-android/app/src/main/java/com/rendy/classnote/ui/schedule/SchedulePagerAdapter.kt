package com.rendy.classnote.ui.schedule

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SchedulePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> WeekViewFragment()
        1 -> CalendarViewFragment()
        else -> WeekViewFragment()
    }
}
