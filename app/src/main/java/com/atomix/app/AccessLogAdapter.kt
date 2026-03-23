package com.atomix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AccessLogAdapter(
    private var logs: List<AccessLog>
) : RecyclerView.Adapter<AccessLogAdapter.LogViewHolder>() {

    private val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_access_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    fun updateLogs(newLogs: List<AccessLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: TextView = itemView.findViewById(R.id.logTypeIcon)
        private val userName: TextView = itemView.findViewById(R.id.logUserName)
        private val userId: TextView = itemView.findViewById(R.id.logUserId)
        private val logTime: TextView = itemView.findViewById(R.id.logTime)

        fun bind(log: AccessLog) {
            userName.text = log.userName
            userId.text = "ID: ${log.userId}"
            logTime.text = timeFormat.format(Date(log.timestamp))
            
            if (log.type == AccessLogType.ENTRY) {
                typeIcon.text = "IN"
                typeIcon.setBackgroundResource(R.drawable.bg_circle_green)
            } else {
                typeIcon.text = "OUT"
                typeIcon.setBackgroundResource(R.drawable.bg_circle_red)
            }
        }
    }
}