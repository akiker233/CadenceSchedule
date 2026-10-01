/** 课程开始广播接收器 */
package com.cadence.schedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CourseStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CourseReminderHelper.updateActiveCountdown(context)
    }
}
