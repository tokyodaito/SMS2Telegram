package life.hnj.sms2telegram.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import life.hnj.sms2telegram.R
import life.hnj.sms2telegram.users.LinkedUser
import java.io.File

class UserAdapter : ListAdapter<LinkedUser, UserAdapter.VH>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar = itemView.findViewById<ImageView>(R.id.user_avatar)
        private val name = itemView.findViewById<TextView>(R.id.user_name)
        private val subtitle = itemView.findViewById<TextView>(R.id.user_subtitle)

        fun bind(user: LinkedUser) {
            name.text = user.displayName
            subtitle.text = user.username?.let { "@$it" } ?: user.chatId

            val path = user.avatarLocalPath
            if (!path.isNullOrBlank()) {
                avatar.load(File(path)) {
                    placeholder(R.mipmap.ic_launcher_round)
                    error(R.mipmap.ic_launcher_round)
                    transformations(CircleCropTransformation())
                }
            } else {
                avatar.load(R.mipmap.ic_launcher_round) {
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LinkedUser>() {
            override fun areItemsTheSame(oldItem: LinkedUser, newItem: LinkedUser): Boolean {
                return oldItem.chatId == newItem.chatId
            }

            override fun areContentsTheSame(oldItem: LinkedUser, newItem: LinkedUser): Boolean {
                return oldItem == newItem
            }
        }
    }
}

